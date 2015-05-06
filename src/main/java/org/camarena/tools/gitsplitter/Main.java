package org.camarena.tools.gitsplitter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings({"HardcodedFileSeparator", "ProhibitedExceptionThrown"})
public class Main {
	public static void main(@Nonnull final String... args) throws GSException {
		new Main().run(args);
	}

	/**
	 * {@link Logger} for this class.
	 *
	 * @return {@link Logger} for this class
	 */
	protected Logger getLogger() {
		return LOGGER;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static final Pattern SPACES_PATTERN = Pattern.compile("\\p{Space}+");
	private final String mLineSeparator = System.getProperty("line.separator");
	private final Map<String, String> mMapFromTempToFinalCommit = new HashMap<>(32);
	private final BiMap<String, String> mFinalHeadToCommit = HashBiMap.create();
	private final BiMap<String, String> mFinalCommitToHead = mFinalHeadToCommit.inverse();

	@Parameter(names = "--source", description = "Url to the original git repository", required = true)
	private String mOriginalRepo = null;

	@Parameter(names = "--git", description = "Path to the git command", required = false)
	private String mGitCommand = "/usr/local/bin/git";

	@Parameter(names = "--rsync", description = "Path to the rsync command", required = false)
	private String mRsyncCommand = "/usr/bin/rsync";

	@Parameter(names = "--tempRepo", description = "Path to the temporary repo.  If specified must not exist",
			          required = false)
	private String mTemporaryRepo = null;

	@Parameter(names = "--finalRepo", description = "Path to the final repo.  Must not exist", required = true)
	private String mFinalRepo = null;

	@Parameter(names = "--topFolder", required = true, description = "Folder that will become new top of the new repo")
	private String mNewTopDirectory = null;

	@Parameter(names = "--branches", required = true, description = "Comma separated branches to include in new repo",
			          variableArity = true)
	private List<String> mBranchesToCopy = null;

	private Path mTempRepoPath = null;
	private Path mFinalRepoPath = null;
	private String mFinalCurrentHead = null;
	private String mTempLastCommitProcessed = null;
	private String mFinalInitialCommit = null;

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void cloneToTempRepo() throws GSException {
		final String dest = mTempRepoPath.normalize().toString();
		getLogger().info("Cloning original repo into temporary one at \"{}\"", dest);
		runOsCommand(mGitCommand, "clone", mOriginalRepo, dest);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void copyCommit(@Nonnull final String commit) {
		try {
			final File tempWorkingDirectory = mTempRepoPath.toFile();
			runOsCommand(tempWorkingDirectory, mGitCommand, "checkout", commit);
			final CommitInfo commitInfo = getCommitInfo(commit);
			getLogger().info("Processing commit \"{}\":\"{}\"", commit, commitInfo.getComment());
			final File finalWorkingDirectory = mFinalRepoPath.toFile();
			if (commitInfo.isMerge()) {
				doMerge(commit, commitInfo);
			} else {
				if (commitInfo.isRoot()) {
					prepareFinalBranch(finalWorkingDirectory, mFinalInitialCommit);
				} else if (!mTempLastCommitProcessed.equals(commitInfo.getParents().get(0))) {
					prepareFinalBranch(finalWorkingDirectory,
					                   mMapFromTempToFinalCommit.get(commitInfo.getParents().get(0)));
					try {
						// This wait is required since the commit sha generation has a time stamp component.  In the
						// rare case that you have two commits with the same parent and the changes are identical you
						// may end up pointing to the same final commit if you created the childs in the same second.
						// It is not very common but I got bitten by it and took me long time to debug it.
						Thread.sleep(1_000);
					} catch (final InterruptedException e) {
						throw Throwables.propagate(e);
					}
				}
				copyFilesAndCommit(commit, commitInfo, s -> true);
			}
			final String currentFinalCommit = getCurrentFinalCommit();
			getLogger().debug("Adding mapping from \"{}\" to commit \"{}\"", mFinalCurrentHead, currentFinalCommit);
			mFinalHeadToCommit.put(mFinalCurrentHead, currentFinalCommit);
			mTempLastCommitProcessed = commit;
			mMapFromTempToFinalCommit.put(commit, currentFinalCommit);
			if (mFinalInitialCommit == null) {
				mFinalInitialCommit = currentFinalCommit;
			}
		} catch (final GSException e) {
			getLogger().error("Failed processing commit \"{}\"", commit);
			throw Throwables.propagate(e);
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void copyFilesAndCommit(@Nonnull final String commit,
	                                @Nonnull final CommitInfo commitInfo,
	                                @Nonnull final Predicate<String> selectModFiles) throws GSException {
		String source = mTempRepoPath.toString();
		if (!(source.length() > 0 && source.charAt(source.length() - 1) == '/')) {
			source += '/';
		}
		String destination = mFinalRepoPath.toString();
		if (!(destination.length() > 0 && destination.charAt(destination.length() - 1) == '/')) {
			destination += '/';
		}
		getLogger().debug("Copying from \"{}\" to \"{}\"", source, destination);
		runOsCommand(mRsyncCommand, "-av", "--delete", "--exclude=.git", source, destination);
		final String status = runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "status", "--short");
		if (StringUtils.isEmpty(status)) {
			getLogger().info("Commit \"{}\" has no changes.  Skipping it", commit);
		} else {
			linesInString(status).map(String::trim).filter(selectModFiles).map(getSecondWord()).forEach(s -> {
				try {
					getLogger().debug("Adding \"{}\"", s);
					runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "add", s);
				} catch (GSException e) {
					throw Throwables.propagate(e);
				}
			});
			final String commitResult = runOsCommand(mFinalRepoPath.toFile(),
			                                         mGitCommand,
			                                         "commit",
			                                         "--author=" + commitInfo.getAuthor(),
			                                         "--date=" + commitInfo.getDate(),
			                                         "-m",
			                                         commitInfo.getComment());
			getLogger().debug("Commit result: \"{}\"", commitResult);
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void createFinalRepo() throws GSException {
		final File workingDirectory = mFinalRepoPath.toFile();
		runOsCommand(workingDirectory, mGitCommand, "init");
		mBranchesToCopy.forEach(b -> {
			getLogger().debug("Creating initial branch \"{}\"", b);
			try {
				runOsCommand(workingDirectory, mGitCommand, "checkout", "-b", b);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private void defineFinalBranches() throws GSException {
		mBranchesToCopy.forEach(b -> {
			try {
				final String originalCommit = getFirstWord().apply(runOsCommand(mTempRepoPath.toFile(),
				                                                                mGitCommand,
				                                                                "log",
				                                                                "--abbrev-commit",
				                                                                "--pretty=oneline",
				                                                                "-1",
				                                                                b));
				final String newCommit = mMapFromTempToFinalCommit.get(originalCommit);
				if (StringUtils.isEmpty(newCommit)) {
					throw new GSException("Can't find new commit");
				}
				getLogger().debug("Setting final branch \"{}\" to commit \"{}\"", b, newCommit);
				final String existingHead = mFinalCommitToHead.get(newCommit);
				runOsCommand(mFinalRepoPath.toFile(),
				             mGitCommand,
				             "checkout",
				             "-b",
				             b,
				             existingHead == null ? newCommit : existingHead);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalHeadToCommit.keySet().forEach(b -> {
			getLogger().debug("Removing temporary branch \"{}\".", b);
			try {
				runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "branch", "-D", b);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
		getLogger().debug("Running GC...");
		runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "gc", "--aggressive", "--prune=all");
	}

	private void displayHelp(@Nonnull final JCommander commander) {
		Objects.requireNonNull(commander);
		final StringBuilder out = new StringBuilder(256);
		commander.usage(out);
		getLogger().info(out.toString());
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void doMerge(@Nonnull final String commit, final CommitInfo commitInfo) throws GSException {
		final ImmutableList<String> headsToMerge = commitInfo.getParents()
				                                           .stream()
				                                           .map(mMapFromTempToFinalCommit::get)
				                                           .map(mFinalCommitToHead::get)
				                                           .collect(immutableListCollector());
		final Optional<String> survivorHeadOption = headsToMerge.stream().min(Comparator.naturalOrder());
		if (!survivorHeadOption.isPresent()) {
			throw new GSException("Can't find head to merge");
		}
		final String survivorHead = survivorHeadOption.get();
		final ImmutableList<String> otherHeads =
				headsToMerge.stream().filter(s -> !survivorHead.equals(s)).collect(immutableListCollector());
		getLogger().debug("Checkout \"{}\" in final repo", survivorHead);
		runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "checkout", survivorHead);
		final ImmutableList<String> args =
				Stream.concat(Stream.of(mGitCommand, "merge", "-m", commitInfo.getComment()), otherHeads.stream())
						.collect(immutableListCollector());
		try {
			getLogger().debug("Issuing the merge command to \"{}\" from {}",
			                 survivorHead,
			                 otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
			runOsCommand(mFinalRepoPath.toFile(), args.toArray(new String[args.size()]));
		} catch (final GSException e) {
			if (e.getMessage().contains("Merge conflict")) {
				getLogger().debug("Found merge conflict.  Issuing a copy and commit");
				copyFilesAndCommit(commit, commitInfo, s -> s.trim().startsWith("UU"));
			} else {
				throw e;
			}
		}
		getLogger().debug("Removing branches and references to {}",
		                 otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		otherHeads.forEach(mFinalHeadToCommit::remove);
		otherHeads.forEach(b -> {
			try {
				runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "branch", "-d", b);
			} catch (final GSException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalCurrentHead = survivorHead;
	}

	private void filterBranch() throws GSException {
		getLogger().debug("Getting current branches");
		final File tempWorkingDirectory = mTempRepoPath.toFile();
		final Set<String> branchesToCreate = new HashSet<>(mBranchesToCopy);
		linesInString(runOsCommand(tempWorkingDirectory, mGitCommand, "branch", "-v")).map(s -> s.substring(1).trim())
				.map(getFirstWord())
				.forEach(branchesToCreate::remove);
		branchesToCreate.forEach(s -> {
			try {
				getLogger().debug("Creating temp branch \"{}\"", s);
				runOsCommand(tempWorkingDirectory, mGitCommand, "checkout", "-b", s, "origin/" + s);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
		getLogger().info("Filtering branch(s):{}",
		                 mBranchesToCopy.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		final ImmutableList<String> args = Stream.concat(Stream.of(mGitCommand,
		                                                           "filter-branch",
		                                                           "--prune-empty",
		                                                           "--subdirectory-filter",
		                                                           mNewTopDirectory), mBranchesToCopy.stream())
				                                   .collect(immutableListCollector());
		//noinspection ResultOfMethodCallIgnored
		runOsCommand(tempWorkingDirectory, args.toArray(new String[args.size()]));
	}

	private CommitInfo getCommitInfo(@Nonnull final String commit) throws GSException {
		final File tempWorkingDirectory = mTempRepoPath.toFile();
		final String resultOfLog = runOsCommand(tempWorkingDirectory, mGitCommand, "log", "-1", commit);
		final ImmutableList<String> lines = linesInString(resultOfLog).collect(immutableListCollector());
		if (lines.size() < 4) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		if (!lines.get(0).startsWith("commit ")) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		int inx = 1;
		final ImmutableList<String> parents;
		if (lines.get(inx).startsWith("Merge:")) {
			parents = Arrays.stream(SPACES_PATTERN.split(lines.get(inx).substring("Merge:".length()).trim()))
					          .collect(immutableListCollector());
			inx++;
		} else {
			parents = getParentOfCommit(commit).map(ImmutableList::of).orElse(ImmutableList.of());
		}
		if (!lines.get(inx).startsWith("Author: ")) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		final String author = lines.get(inx).substring("Author: ".length()).trim();
		inx++;
		if (!lines.get(inx).startsWith("Date: ")) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		final String date = lines.get(inx).substring("Date: ".length()).trim();
		inx++;
		if (!StringUtils.isEmpty(lines.get(inx).trim())) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		inx++;
		final String comment = lines.stream().skip(inx).map(String::trim).collect(joining(mLineSeparator));
		return new CommitInfo(author, date, comment, parents);
	}

	private ImmutableList<String> getCommitListInReverse() throws GSException {
		final File tempRepo = mTempRepoPath.toFile();
		final Set<String> relevantCommits = mBranchesToCopy.stream().flatMap(s -> {
			try {
				return linesInString(runOsCommand(tempRepo,
				                                  mGitCommand,
				                                  "log",
				                                  "--abbrev-commit",
				                                  "--pretty=oneline",
				                                  s)).map(getFirstWord());
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		}).collect(toSet());
		final String allCommits =
				runOsCommand(tempRepo, mGitCommand, "log", "--abbrev-commit", "--pretty=oneline", "--all");
		return linesInString(allCommits).map(getFirstWord())
				       .filter(relevantCommits::contains)
				       .collect(immutableListCollector())
				       .reverse();
	}

	@Nonnull
	private String getCurrentFinalCommit() throws GSException {
		final String commit = runOsCommand(mFinalRepoPath.toFile(),
		                                   mGitCommand,
		                                   "log",
		                                   "--abbrev-commit",
		                                   "--pretty=oneline",
		                                   "-1",
		                                   "HEAD");
		return getFirstWord().apply(commit);
	}

	private Function<String, String> getFirstWord() {
		return getNWord(0);
	}

	private Function<String, String> getNWord(final int n) {
		return s -> {
			final String[] parts = SPACES_PATTERN.split(s);
			return parts[n];
		};
	}

	@Nonnull
	private String getNewHead() {
		for (int inx = 1; ; inx++) {
			final String newHead = "Branch_" + inx;
			if (!mFinalHeadToCommit.containsKey(newHead)) {
				getLogger().debug("New head to use is \"{}\"", newHead);
				return newHead;
			}
		}
	}

	@Nonnull
	private Optional<String> getParentOfCommit(@Nonnull final String commit) {
		try {
			final String parent = runOsCommand(mTempRepoPath.toFile(),
			                                   mGitCommand,
			                                   "log",
			                                   "--abbrev-commit",
			                                   "--pretty=oneline",
			                                   "-1",
			                                   commit + '^');
			return Optional.of(getFirstWord().apply(parent));
		} catch (final GSException e) {
			getLogger().debug("Can't get parent of commit \"{}\".  Assuming is the first one", commit);
		}
		return Optional.empty();
	}

	private Function<String, String> getSecondWord() {
		return getNWord(1);
	}

	private Collector<String, ImmutableList.Builder<String>, ImmutableList<String>> immutableListCollector() {
		return Collector.of(ImmutableList.Builder::new,
		                    (BiConsumer<ImmutableList.Builder<String>, String>) ImmutableList.Builder::add,

		                    (s1, s2) -> s1.addAll(s2.build()),
		                    ImmutableList.Builder::build);
	}

	private Stream<String> linesInString(@Nonnull final String s) {
		Objects.requireNonNull(s);
		return new BufferedReader(new StringReader(s)).lines();
	}

	private void prepareFinalBranch(@Nonnull final File finalWorkingDirectory, @Nullable final String baseCommit)
			throws GSException {
		mFinalCurrentHead = getNewHead();
		if (baseCommit == null) {
			runOsCommand(finalWorkingDirectory, mGitCommand, "checkout", "-b", mFinalCurrentHead);
		} else {
			runOsCommand(finalWorkingDirectory, mGitCommand, "checkout", "-b", mFinalCurrentHead, baseCommit);
		}
	}

	private void removeOrigin() throws GSException {
		getLogger().debug("Getting remotes");
		final File workingDirectory = mTempRepoPath.toFile();
		final String remoteResults = runOsCommand(workingDirectory, mGitCommand, "remote", "-v");
		final Set<String> remotes = linesInString(remoteResults).map(getFirstWord()).collect(toSet());
		remotes.stream().forEach(s -> {
			getLogger().debug("Removing remote \"{}" + '\"', s);
			try {
				//noinspection ResultOfMethodCallIgnored
				runOsCommand(workingDirectory, mGitCommand, "remote", "remove", s);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private void run(final String... args) throws GSException {
		final JCommander commander = new JCommander();
		commander.addObject(this);
		try {
			commander.parse(args);
		} catch (final ParameterException e) {
			getLogger().error("Invalid configuration", e);
			displayHelp(commander);
			throw new GSInvalidArgumentException("Can't recognize configuration. ", e);
		}
		validateParameters();
		cloneToTempRepo();
		filterBranch();
		removeOrigin();
		createFinalRepo();
		final ImmutableList<String> commitList = getCommitListInReverse();
		commitList.stream().forEach(this::copyCommit);
		defineFinalBranches();
	}

	private String runOsCommand(@Nonnull final File workingDirectory, @Nonnull final String... args)
			throws GSException {
		Objects.requireNonNull(workingDirectory);
		return runOsCommand(Optional.of(workingDirectory), args);
	}

	private String runOsCommand(@Nonnull final String... args) throws GSException {
		return runOsCommand(Optional.empty(), args);
	}

	private String runOsCommand(@Nonnull final Optional<File> workingDirectory, @Nonnull final String... args)
			throws GSException {
		try {
			final ProcessBuilder processBuilder = new ProcessBuilder(args);
			workingDirectory.ifPresent(processBuilder::directory);
			final Process process = processBuilder.start();
			final byte[] result = ByteStreams.toByteArray(process.getInputStream());
			final String error = new String(ByteStreams.toByteArray(process.getErrorStream()));
			process.waitFor();
			final int exitValue = process.exitValue();
			if (exitValue != 0) {
				throw new GSException("Invocation of \"" + Arrays.stream(args).collect(joining(" ")) +
				                      "\" resulted in error(" +
				                      exitValue +
				                      "). stderr:" +
				                      error + "\nstdout:" + new String(result));
			}
			if (!StringUtils.isEmpty(error)) {
				getLogger().debug("STDERR: \"{}\"", error);
			}
			return new String(result);
		} catch (IOException | InterruptedException e) {
			throw new GSException(e);
		}
	}

	private void validateParameters() throws GSInvalidArgumentException {
		try {
			final Path tempRepo;
			if (mTemporaryRepo == null) {
				tempRepo = Files.createTempDirectory("GitSplitterTempRepo");
			} else {
				tempRepo = Paths.get(mTemporaryRepo);
				if (Files.exists(tempRepo)) {
					throw new GSInvalidArgumentException("Temporary path \"" + mTemporaryRepo + "\" should not exist");
				}
			}
			mTempRepoPath = Files.createDirectories(tempRepo);
			if (StringUtils.isEmpty(mFinalRepo)) {
				throw new GSInvalidArgumentException("Final repo argument can't be empty");
			}
			final Path finalRepo = Paths.get(mFinalRepo);
			if (Files.exists(finalRepo)) {
				throw new GSInvalidArgumentException("Final repo \"" + mFinalRepo + "\"should not exist");
			}
			mFinalRepoPath = Files.createDirectories(finalRepo);
		} catch (final IOException e) {
			throw new GSInvalidArgumentException("Can't create folder", e);
		}
	}

}
