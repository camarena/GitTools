package org.camarena.tools.gitsplitter;

import com.beust.jcommander.Parameter;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.camarena.tools.CLIException;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.CLITool;
import org.camarena.tools.Configuration;
import org.camarena.tools.oscommands.ProcessResult;
import org.camarena.tools.oscommands.git.CommitInfo;
import org.camarena.tools.oscommands.git.GitBranchDeleteOption;
import org.camarena.tools.oscommands.git.GitVerboseOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.camarena.tools.FunctionUtils.getFirstWord;
import static org.camarena.tools.FunctionUtils.getSecondWord;
import static org.camarena.tools.StreamUtils.immutableListCollector;
import static org.camarena.tools.StreamUtils.linesInString;
import static org.camarena.tools.oscommands.git.GitArgument.arguments;
import static org.camarena.tools.oscommands.git.GitBranchForceDeleteOption.forceDelete;
import static org.camarena.tools.oscommands.git.GitCheckoutBranchOption.createBranch;
import static org.camarena.tools.oscommands.git.GitCommand.gitCommand;
import static org.camarena.tools.oscommands.git.GitCommitAuthorOption.author;
import static org.camarena.tools.oscommands.git.GitCommitDateOption.date;
import static org.camarena.tools.oscommands.git.GitFilterBranchPruneEmptyOption.pruneEmpty;
import static org.camarena.tools.oscommands.git.GitFilterBranchSubdirectoryFilterOption.subdirectoryFilter;
import static org.camarena.tools.oscommands.git.GitGCAggressiveOption.aggressive;
import static org.camarena.tools.oscommands.git.GitGCPruneOption.pruneAll;
import static org.camarena.tools.oscommands.git.GitLogAbbrevCommitOption.abbrev;
import static org.camarena.tools.oscommands.git.GitLogAllOption.all;
import static org.camarena.tools.oscommands.git.GitLogMaxCountOption.maxCount;
import static org.camarena.tools.oscommands.git.GitLogPrettyOption.pretty;
import static org.camarena.tools.oscommands.git.GitMessageOption.message;
import static org.camarena.tools.oscommands.git.GitRemoteRemoveOption.remove;
import static org.camarena.tools.oscommands.git.GitStatusShortOption.shortStatus;
import static org.camarena.tools.oscommands.rsync.RSyncArchiveOption.archive;
import static org.camarena.tools.oscommands.rsync.RSyncCommand.rsyncCommand;
import static org.camarena.tools.oscommands.rsync.RSyncDeleteOption.delete;
import static org.camarena.tools.oscommands.rsync.RSyncExcludeOption.exclude;
import static org.camarena.tools.oscommands.rsync.RSyncVerboseOption.verbose;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings({"HardcodedFileSeparator", "ProhibitedExceptionThrown"})
public
class Main extends CLITool implements Configuration {
	private static final Logger                LOGGER                    = LoggerFactory.getLogger(Main.class);
	private final        Map<String, String>   mMapFromTempToFinalCommit = new HashMap<>(32);
	private final        BiMap<String, String> mFinalHeadToCommit        = HashBiMap.create();
	private final        BiMap<String, String> mFinalCommitToHead        = mFinalHeadToCommit.inverse();
	@Parameter(names = "--source", description = "Url to the original git repository", required = true)
	private              String                mOriginalRepo             = null;
	@Parameter(names = "--tempRepo", description = "Path to the temporary repo.  If specified must not exist",
	           required = false)
	private              String                mTemporaryRepo            = null;
	@Parameter(names = "--finalRepo", description = "Path to the final repo.  Must not exist", required = true)
	private              String                mFinalRepo                = null;
	@Parameter(names = "--topFolder", required = true, description = "Folder that will become new top of the new repo")
	private              String                mNewTopDirectory          = null;
	@Parameter(names = "--branches", required = true, description = "Comma separated branches to include in new repo",
	           variableArity = true)
	private              List<String>          mBranchesToCopy           = null;
	@Parameter(names = "--mapFile", required = true, description = "Path to the file to store the mappings from " +
	                                                               "original commit to new one")
	private              String                mMappingFile              = null;
	private              Path                  mTempRepoPath             = null;
	private              Path                  mFinalRepoPath            = null;
	private              String                mFinalCurrentHead         = null;
	private              String                mTempLastCommitProcessed  = null;
	private              String                mFinalInitialCommit       = null;
	private              PrintWriter           mMappingFileWriter        = null;

	public static
	void main(@Nonnull final String... args) throws CLIException {
		final Main cliTool = new Main();
		cliTool.run(args, gitCommand(), rsyncCommand(), cliTool);
	}

	@Override
	public
	void validate() throws CLIInvalidArgumentException {
		try {
			final Path tempRepo;
			if (mTemporaryRepo == null) {
				tempRepo = Files.createTempDirectory("GitSplitterTempRepo");
			}
			else {
				tempRepo = Paths.get(mTemporaryRepo);
				if (Files.exists(tempRepo)) {
					throw new CLIInvalidArgumentException("Temporary path \"" + mTemporaryRepo + "\" should not " +
					                                      "exist");
				}
			}
			mTempRepoPath = Files.createDirectories(tempRepo).normalize();
			if (StringUtils.isEmpty(mFinalRepo)) {
				throw new CLIInvalidArgumentException("Final repo argument can't be empty");
			}
			final Path finalRepo = Paths.get(mFinalRepo);
			if (Files.exists(finalRepo)) {
				throw new CLIInvalidArgumentException("Final repo \"" + mFinalRepo + "\"should not exist");
			}
			mFinalRepoPath = Files.createDirectories(finalRepo);
			final Path mappingFile = Paths.get(mMappingFile);
			if (Files.exists(mappingFile)) {
				throw new CLIInvalidArgumentException("Mapping file \"" + mMappingFile + "\" should not exist");
			}
			mMappingFileWriter = new PrintWriter(Files.newBufferedWriter(mappingFile, StandardOpenOption.CREATE_NEW));
		} catch (final IOException e) {
			throw new CLIInvalidArgumentException("Can't create folder", e);
		}
	}

	@Override
	protected
	void cleanUp() throws CLIException {
		try {
			Files.walkFileTree(mTempRepoPath, new SimpleFileVisitor<Path>() {
				@Override
				public
				FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public
				FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

			});
		} catch (final IOException e) {
			//noinspection ThrowFromFinallyBlock
			throw new CLIException(e);
		}
	}

	@Override
	protected
	void execute() throws CLIException {
		cloneToTempRepo();
		filterBranch();
		removeOrigin();
		createFinalRepo();
		final ImmutableList<String> commitList = getCommitListInReverse();
		commitList.stream().forEach(this::copyCommit);
		defineFinalBranches();
		mMappingFileWriter.close();
	}

	/**
	 * {@link Logger} for this class.
	 *
	 * @return {@link Logger} for this class
	 */
	@Override
	protected
	Logger getLogger() {
		return LOGGER;
	}

	private
	void cloneToTempRepo() throws CLIException {
		getLogger().info("Cloning original repo into temporary one at \"{}\"", mTempRepoPath);
		reviewResult(gitCommand().cloneRepo(mOriginalRepo, mTempRepoPath));
	}

	@Nonnull
	private
	String reviewResult(final CompletableFuture<ProcessResult> result) throws CLIException {
		try {
			final ProcessResult processResult = result.get();
			if (processResult.getExitValue() != 0) {
				final String command = Arrays.stream(processResult.getArgs()).collect(joining("', '" + '\'' + '\''));
				throw new CLIException("Command failed:"
				                       + command
				                       + "\nstdOut:"
				                       + processResult.getStdOut()
				                       + "\nstdErr:"
				                       + processResult.getStdErr());
			}
			return processResult.getStdOut();
		} catch (InterruptedException | ExecutionException e) {
			throw new CLIException(e);
		}
	}

	private
	void copyCommit(@Nonnull final String commit) {
		try {
			reviewResult(gitCommand().checkout(mTempRepoPath, arguments(commit)));
			final CommitInfo commitInfo = gitCommand().getCommitInfo(mTempRepoPath, commit).get();
			getLogger().info("Processing commit \"{}\":\"{}\"", commit, commitInfo.getComment());
			if (commitInfo.isMerge()) {
				doMerge(commit, commitInfo);
			}
			else {
				if (commitInfo.isRoot()) {
					prepareFinalBranch(mFinalRepoPath, mFinalInitialCommit);
				}
				else if (!mTempLastCommitProcessed.equals(commitInfo.getParents().get(0))) {
					prepareFinalBranch(mFinalRepoPath,
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
			final String currentFinalCommit = getFinalCurrentCommit();
			getLogger().debug("Adding mapping from \"{}\" to commit \"{}\"", mFinalCurrentHead, currentFinalCommit);
			mFinalHeadToCommit.put(mFinalCurrentHead, currentFinalCommit);
			mTempLastCommitProcessed = commit;
			mMapFromTempToFinalCommit.put(commit, currentFinalCommit);
			if (mFinalInitialCommit == null) {
				mFinalInitialCommit = currentFinalCommit;
			}
			mMappingFileWriter.println(commit + '\t' + currentFinalCommit);
		} catch (final InterruptedException | ExecutionException | CLIException e) {
			getLogger().error("Failed processing commit \"{}\"", commit);
			throw Throwables.propagate(e);
		}
	}

	private
	void copyFilesAndCommit(@Nonnull final String commit,
	                        @Nonnull final CommitInfo commitInfo,
	                        @Nonnull final Predicate<String> selectModFiles) throws CLIException {
		String source = mTempRepoPath.toString();
		if (!(source.length() > 0 && source.charAt(source.length() - 1) == '/')) {
			source += '/';
		}
		String destination = mFinalRepoPath.toString();
		if (!(destination.length() > 0 && destination.charAt(destination.length() - 1) == '/')) {
			destination += '/';
		}
		getLogger().debug("Copying from \"{}\" to \"{}\"", source, destination);
		reviewResult(rsyncCommand().sync(source, destination, archive,
		                                 verbose, delete, exclude(".git")));
		final String status = reviewResult(gitCommand().status(mFinalRepoPath, shortStatus));
		if (StringUtils.isEmpty(status)) {
			getLogger().info("Commit \"{}\" has no changes.  Skipping it", commit);
		}
		else {
			linesInString(status).map(String::trim).filter(selectModFiles).map(getSecondWord()).forEach(s -> {
				try {
					getLogger().debug("Adding \"{}\"", s);
					reviewResult(gitCommand().add(mFinalRepoPath, s));
				} catch (CLIException e) {
					throw Throwables.propagate(e);
				}
			});
			final String commitResult = reviewResult(gitCommand().commit(mFinalRepoPath,
			                                                             author(commitInfo.getAuthor()),
			                                                             date(commitInfo.getDate()),
			                                                             message(commitInfo.getComment())));
			getLogger().debug("Commit result: \"{}\"", commitResult);
		}
	}

	private
	void createFinalRepo() throws CLIException {
		reviewResult(gitCommand().init(mFinalRepoPath));
		mBranchesToCopy.forEach(b -> {
			getLogger().debug("Creating initial branch \"{}\"", b);
			try {
				reviewResult(gitCommand().checkout(mFinalRepoPath, createBranch, arguments(b)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private
	void defineFinalBranches() throws CLIException {
		mBranchesToCopy.forEach(b -> {
			try {
				final String originalCommit = getFirstWord().apply(reviewResult(gitCommand().log(mTempRepoPath,
				                                                                                 abbrev,
				                                                                                 pretty("oneline"),
				                                                                                 maxCount(1),
				                                                                                 arguments(b))));
				final String newCommit = mMapFromTempToFinalCommit.get(originalCommit);
				if (StringUtils.isEmpty(newCommit)) {
					throw new CLIException("Can't find new commit");
				}
				getLogger().debug("Setting final branch \"{}\" to commit \"{}\"", b, newCommit);
				final String existingHead = mFinalCommitToHead.get(newCommit);
				reviewResult(gitCommand().checkout(mFinalRepoPath, createBranch, arguments(b,
				                                                                           existingHead == null ?
				                                                                           newCommit :
				                                                                           existingHead)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalHeadToCommit.keySet().forEach(b -> {
			getLogger().debug("Removing temporary branch \"{}\".", b);
			try {
				reviewResult(gitCommand().branch(mFinalRepoPath, forceDelete, arguments(b)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		getLogger().debug("Running GC...");
		reviewResult(gitCommand().gc(mFinalRepoPath, aggressive, pruneAll));
	}

	private
	void doMerge(@Nonnull final String commit, final CommitInfo commitInfo) throws CLIException {
		final ImmutableList<String> headsToMerge = commitInfo.getParents()
		                                                     .stream()
		                                                     .map(mMapFromTempToFinalCommit::get)
		                                                     .map(mFinalCommitToHead::get)
		                                                     .collect(immutableListCollector());
		final Optional<String> survivorHeadOption = headsToMerge.stream().min(Comparator.naturalOrder());
		if (!survivorHeadOption.isPresent()) {
			throw new CLIException("Can't find head to merge");
		}
		final String survivorHead = survivorHeadOption.get();
		final ImmutableList<String> otherHeads =
				headsToMerge.stream().filter(s -> !survivorHead.equals(s)).collect(immutableListCollector());
		getLogger().debug("Checkout \"{}\" in final repo", survivorHead);
		reviewResult(gitCommand().checkout(mFinalRepoPath, arguments(survivorHead)));
		try {
			getLogger().debug("Issuing the merge command to \"{}\" from {}",
			                  survivorHead,
			                  otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
			reviewResult(gitCommand().merge(mFinalRepoPath, message(commitInfo.getComment()), arguments(otherHeads
					                                                                                            .stream())));
		} catch (final CLIException e) {
			if (e.getMessage().contains("Merge conflict")) {
				getLogger().debug("Found merge conflict.  Issuing a copy and commit");
				copyFilesAndCommit(commit, commitInfo, s -> s.trim().startsWith("UU"));
			}
			else {
				throw e;
			}
		}
		getLogger().debug("Removing branches and references to {}",
		                  otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		otherHeads.forEach(mFinalHeadToCommit::remove);
		otherHeads.forEach(b -> {
			try {
				reviewResult(gitCommand().branch(mFinalRepoPath, GitBranchDeleteOption.deleteBranch, arguments(b)));
			} catch (final CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalCurrentHead = survivorHead;
	}

	private
	void filterBranch() throws CLIException {
		getLogger().debug("Getting current branches");
		final Set<String> branchesToCreate = new HashSet<>(mBranchesToCopy);
		final String branchesInfo = reviewResult(gitCommand().branch(mTempRepoPath,
		                                                             GitVerboseOption.verbose));
		linesInString(branchesInfo).map(s -> s.substring(1).trim())
		                           .map(getFirstWord())
		                           .forEach(branchesToCreate::remove);
		branchesToCreate.forEach(s -> {
			try {
				getLogger().debug("Creating temp branch \"{}\"", s);
				reviewResult(gitCommand().checkout(mTempRepoPath, createBranch, arguments(s, "origin/" + s)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		getLogger().info("Filtering branch(s):{}",
		                 mBranchesToCopy.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		reviewResult(gitCommand().filterBranch(mTempRepoPath,
		                                       pruneEmpty, subdirectoryFilter(mNewTopDirectory),
		                                       arguments(mBranchesToCopy.stream())));
	}

	private
	ImmutableList<String> getCommitListInReverse() throws CLIException {
		final Set<String> relevantCommits = mBranchesToCopy.stream().flatMap(s -> {
			try {
				return linesInString(reviewResult(gitCommand().log(mTempRepoPath,
				                                                   abbrev,
				                                                   pretty("oneline"),
				                                                   arguments(s)))).map(getFirstWord());
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		}).collect(toSet());
		final String allCommits = reviewResult(gitCommand().log(mTempRepoPath, abbrev, pretty("oneline"),
		                                                        all));
		return linesInString(allCommits).map(getFirstWord())
		                                .filter(relevantCommits::contains)
		                                .collect(immutableListCollector())
		                                .reverse();
	}

	private
	String getCurrentCommit(final Path gitPath) throws CLIException {
		final String commit =
				reviewResult(gitCommand().log(gitPath, abbrev, pretty("oneline"), maxCount(1), arguments("HEAD")));
		return getFirstWord().apply(commit);
	}

	@Nonnull
	private
	String getFinalCurrentCommit() throws CLIException {
		return getCurrentCommit(mFinalRepoPath);
	}

	@Nonnull
	private
	String getNewHead() {
		//noinspection ForLoopWithMissingComponent
		for (int inx = 1; ; inx++) {
			final String newHead = "Branch_" + inx;
			if (!mFinalHeadToCommit.containsKey(newHead)) {
				getLogger().debug("New head to use is \"{}\"", newHead);
				return newHead;
			}
		}
	}

	private
	void prepareFinalBranch(@Nonnull final Path finalPath, @Nullable final String baseCommit)
			throws CLIException {
		mFinalCurrentHead = getNewHead();
		if (baseCommit == null) {
			reviewResult(gitCommand().checkout(finalPath, createBranch, arguments(mFinalCurrentHead)));
		}
		else {
			reviewResult(gitCommand().checkout(finalPath, createBranch, arguments(mFinalCurrentHead, baseCommit)));
		}
	}

	private
	void removeOrigin() throws CLIException {
		getLogger().debug("Getting remotes");
		final String remoteResults = reviewResult(gitCommand().remote(mTempRepoPath, GitVerboseOption
				.verbose));
		final Set<String> remotes = linesInString(remoteResults).map(getFirstWord()).collect(toSet());
		remotes.stream().forEach(s -> {
			getLogger().debug("Removing remote \"{}" + '\"', s);
			try {
				reviewResult(gitCommand().remote(mTempRepoPath, remove, arguments(s)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

}
