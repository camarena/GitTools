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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * @author Hermán de J. Camarena R.
 */
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
	@Parameter(names = "--source", description = "Url to the original git repository", required = true)
	private String mOriginalRepo;
	@Parameter(names = "--git", description = "Path to the git command", required = false)
	private String mGitCommand = "/usr/local/bin/git";
	@Parameter(names = "--rsync", description = "Path to the rsync command", required = false)
	private String mRsyncCommand = "/usr/bin/rsync";
	@Parameter(names = "--tempRepo", description = "Path to the temporary repo.  Must not exist", required = false)
	private String mTemporaryRepo = null;
	@Parameter(names = "--finalRepo", description = "Path to the final repo.  Must not exist", required = true)
	private String mFinalRepo = null;
	@Parameter(names = "--topFolder", required = true, description = "Folder that will become new top of the tree")
	private String mNewTopDirectory;
	@Parameter(names = "--branch", required = true, description = "Branch to process")
	private String mBranchToCopy;
	private Path mTempRepoPath;
	private Path mFinalRepoPath;
	private final String mLineSeparator = System.getProperty("line.separator");
	private final Map<String, String> mMapFromTempToFinalCommit = new HashMap<>();
	private final BiMap<String, String> mFinalHeadToCommit = HashBiMap.create();
	private final BiMap<String, String> mFinalCommitToHead = mFinalHeadToCommit.inverse();
	private String mFinalCurrentHead = null;
	private String mTempLastCommitProcessed = null;
	private String mFinalInitialCommit = null;

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void cloneToTempRepo() throws GSException {
		final String dest = mTempRepoPath.normalize().toString();
		getLogger().info("Cloning original repo into temporary one at \"" + dest + '\'');
		runOsCommand(mGitCommand, "clone", mOriginalRepo, dest);
		getLogger().info("Checking out \"" + mBranchToCopy + "\" in Temp repo");
		runOsCommand(mTempRepoPath.toFile(), mGitCommand, "checkout", "-b", mBranchToCopy, "origin/" + mBranchToCopy);
		mFinalCurrentHead = mBranchToCopy;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void copyCommit(@Nonnull final String commit) {
		try {
			getLogger().info("Checking out commit \"" + commit + "\" in Temp repo");
			final File tempWorkingDirectory = mTempRepoPath.toFile();
			runOsCommand(tempWorkingDirectory, mGitCommand, "checkout", commit);
			final CommitInfo commitInfo = getCommitInfo(commit);
			final File finalWorkingDirectory = mFinalRepoPath.toFile();
			if (commitInfo.isMerge()) {
				doMerge(commit, commitInfo);
			} else {
				if (commitInfo.isRoot()) {
					prepareFinalBranch(finalWorkingDirectory);
				} else if (!mTempLastCommitProcessed.equals(commitInfo.getParents().get(0))) {
					prepareFinalBranch(finalWorkingDirectory,
					                   mMapFromTempToFinalCommit.get(commitInfo.getParents().get(0)));
					try {
						// This wait is required since the commit sha generation has a time stamp component.  In the
						// rare case that you have two commits with the same parent and the changes are identical you
						// may end up pointing to the same final commit if you created the childs in the same second.
						// It is not very common but I got bitten by it and took me long time to debug it.
						Thread.sleep(1_000);
					} catch (InterruptedException e) {
						throw Throwables.propagate(e);
					}
				}
				copyFilesAndCommit(commit, commitInfo, s -> true);
			}
			final String currentFinalCommit = getCurrentFinalCommit();
			getLogger().info("Adding mapping from \"" +
			                 mFinalCurrentHead +
			                 "\" to commit \"" +
			                 currentFinalCommit +
			                 '"');
			mFinalHeadToCommit.put(mFinalCurrentHead, currentFinalCommit);
			mTempLastCommitProcessed = commit;
			mMapFromTempToFinalCommit.put(commit, currentFinalCommit);
			if (mFinalInitialCommit == null) {
				mFinalInitialCommit = currentFinalCommit;
			}
		} catch (GSException e) {
			getLogger().error("Failed copying commit \"" + commit + '"');
			throw Throwables.propagate(e);
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void copyFilesAndCommit(final @Nonnull String commit,
	                                @Nonnull final CommitInfo commitInfo,
	                                @Nonnull final Predicate<String> selectModFiles) throws GSException {
		String source = mTempRepoPath.toString();
		if (!source.endsWith("/")) {
			source += '/';
		}
		String destination = mFinalRepoPath.toString();
		if (!destination.endsWith("/")) {
			destination += '/';
		}
		getLogger().info("Copying from \"" + source + "\" to \"" + destination + '"');
		runOsCommand(mRsyncCommand, "-av", "--delete", "--exclude=.git", source, destination);
		final String status = runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "status", "--short");
		if (StringUtils.isEmpty(status)) {
			getLogger().info("Commit \"" + commit + "\" has no changes.  Skipping it");
		} else {
			linesInString(status).map(String::trim).filter(selectModFiles).map(getSecondWord()).forEach(s -> {
				try {
					getLogger().info("Adding \"" + s + '"');
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
			getLogger().info("Commit result: \"" + commitResult + '"');
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void createFinalRepo() throws GSException {
		final File workingDirectory = mFinalRepoPath.toFile();
		runOsCommand(workingDirectory, mGitCommand, "init");
		runOsCommand(workingDirectory, mGitCommand, "checkout", "-b", mBranchToCopy);
	}

	private void displayHelp(@Nonnull final JCommander commander) {
		Objects.requireNonNull(commander);
		final StringBuilder out = new StringBuilder(256);
		commander.usage(out);
		getLogger().error(out.toString());
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void doMerge(final @Nonnull String commit, final CommitInfo commitInfo) throws GSException {
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
		getLogger().info("Checkout \"" + survivorHead + "\" in final repo");
		runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "checkout", survivorHead);
		final ImmutableList<String> args =
				Stream.concat(Stream.of(mGitCommand, "merge", "-m", commitInfo.getComment()), otherHeads.stream())
						.collect(immutableListCollector());
		try {
			getLogger().info("Issuing the merge command to \"" +
			                 survivorHead +
			                 "\" from " +
			                 otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
			runOsCommand(mFinalRepoPath.toFile(), args.toArray(new String[args.size()]));
		} catch (GSException e) {
			if (e.getMessage().contains("Merge conflict")) {
				getLogger().info("Found merge conflict.  Issuing a copy and commit");
				copyFilesAndCommit(commit, commitInfo, s -> s.trim().startsWith("UU"));
			} else {
				throw e;
			}
		}
		getLogger().info("Removing branches and references to " +
		                 otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		otherHeads.forEach(mFinalHeadToCommit::remove);
		otherHeads.forEach(b -> {
			try {
				runOsCommand(mFinalRepoPath.toFile(), mGitCommand, "branch", "-d", b);
			} catch (GSException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalCurrentHead = survivorHead;
	}

	private void filterBranch() throws GSException {
		getLogger().info("Filtering branch \"" + mBranchToCopy + '\'');
		//noinspection ResultOfMethodCallIgnored
		runOsCommand(mTempRepoPath.toFile(),
		             mGitCommand,
		             "filter-branch",
		             "--prune-empty",
		             "--subdirectory-filter",
		             mNewTopDirectory,
		             mBranchToCopy);
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
			parents = Arrays.stream(lines.get(inx).substring("Merge:".length()).trim().split("[ \\t]+"))
					          .collect(immutableListCollector());
			inx++;
		} else {
			parents = getParentOfCommit(commit).map(ImmutableList::of).orElse(ImmutableList.of());
		}
		if (!lines.get(inx).startsWith("Author: ")) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		String author = lines.get(inx).substring("Author: ".length()).trim();
		inx++;
		if (!lines.get(inx).startsWith("Date: ")) {
			throw new GSException("Can't parse result from log -1.\n" + resultOfLog);
		}
		String date = lines.get(inx).substring("Date: ".length()).trim();
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
		final String allCommits = runOsCommand(tempRepo, mGitCommand, "log", "--abbrev-commit", "--pretty=oneline");
		return linesInString(allCommits).map(getFirstWord()).collect(immutableListCollector()).reverse();
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
			final String[] parts = s.split("[ \\t]+");
			return parts[n];
		};
	}

	@Nonnull
	private String getNewHead() {
		if (mFinalHeadToCommit.size() == 0) {
			getLogger().info("New head to use is \"" + mBranchToCopy + '"');
			return mBranchToCopy;
		}
		int inx = 1;
		for (; ; ) {
			final String newHead = mBranchToCopy + '_' + inx;
			if (!mFinalHeadToCommit.containsKey(newHead)) {
				getLogger().info("New head to use is \"" + newHead + '"');
				return newHead;
			}
			inx++;
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
		} catch (GSException e) {
			getLogger().info("Can't get parent of commit \"" + commit + "\".  Assuming is the first one");
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

	private void prepareFinalBranch(final File finalWorkingDirectory, final String baseCommit) throws GSException {
		mFinalCurrentHead = getNewHead();
		if (!mFinalCurrentHead.equals(mBranchToCopy)) {
			//noinspection ResultOfMethodCallIgnored
			runOsCommand(finalWorkingDirectory, mGitCommand, "checkout", "-b", mFinalCurrentHead, baseCommit);
		}
	}

	private void prepareFinalBranch(final File finalWorkingDirectory) throws GSException {
		prepareFinalBranch(finalWorkingDirectory, mFinalInitialCommit);
	}

	private void removeOrigin() throws GSException {
		getLogger().info("Getting remotes");
		final File workingDirectory = mTempRepoPath.toFile();
		final String remoteResults = runOsCommand(workingDirectory, mGitCommand, "remote", "-v");
		final Set<String> remotes = linesInString(remoteResults).map(getFirstWord()).collect(toSet());
		remotes.stream().forEach(s -> {
			getLogger().info("Removing remote \"" + s + '\"');
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
	}

	private String runOsCommand(@Nonnull File workingDirectory, @Nonnull final String... args) throws GSException {
		Objects.requireNonNull(workingDirectory);
		return runOsCommand(Optional.of(workingDirectory), args);
	}

	private String runOsCommand(@Nonnull final String... args) throws GSException {
		return runOsCommand(Optional.empty(), args);
	}

	private String runOsCommand(@Nonnull Optional<File> workingDirectory, @Nonnull final String... args)
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
				getLogger().info("STDERR: \"" + error + '"');
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
		} catch (IOException e) {
			throw new GSInvalidArgumentException("Can't create folder", e);
		}
	}

}
