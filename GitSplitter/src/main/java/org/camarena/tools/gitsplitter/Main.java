package org.camarena.tools.gitsplitter;

import com.beust.jcommander.Parameter;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.camarena.tools.CLIException;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.CLITool;
import org.camarena.tools.Configuration;
import org.camarena.tools.FunctionUtils;
import org.camarena.tools.oscommands.ProcessResult;
import org.camarena.tools.oscommands.Tuple2;
import org.camarena.tools.oscommands.git.CommitInfo;
import org.camarena.tools.oscommands.git.GitBranchDeleteOption;
import org.camarena.tools.oscommands.git.GitVerboseOption;
import org.camarena.tools.oscommands.rsync.RSyncExcludeOption;
import org.camarena.tools.oscommands.rsync.RSyncOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.camarena.tools.FunctionUtils.getFirstWord;
import static org.camarena.tools.StreamUtils.immutableListCollector;
import static org.camarena.tools.StreamUtils.immutableSetCollector;
import static org.camarena.tools.StreamUtils.linesInString;
import static org.camarena.tools.oscommands.git.GitAddAllFullTreeOption.allFullTree;
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
import static org.camarena.tools.oscommands.git.GitLogFormatOption.format;
import static org.camarena.tools.oscommands.git.GitLogMaxCountOption.maxCount;
import static org.camarena.tools.oscommands.git.GitLogPrettyOption.pretty;
import static org.camarena.tools.oscommands.git.GitMessageOption.message;
import static org.camarena.tools.oscommands.git.GitRemoteRemoveOption.remove;
import static org.camarena.tools.oscommands.git.GitStatusShortOption.shortStatus;
import static org.camarena.tools.oscommands.rsync.RSyncArchiveOption.archive;
import static org.camarena.tools.oscommands.rsync.RSyncCommand.rsyncCommand;
import static org.camarena.tools.oscommands.rsync.RSyncDeleteOption.delete;
import static org.camarena.tools.oscommands.rsync.RSyncExcludeOption.exclude;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings({"HardcodedFileSeparator", "ProhibitedExceptionThrown"})
public
class Main extends CLITool implements Configuration {
	private static final Logger                LOGGER                    = LoggerFactory.getLogger(Main.class);
	private final        Map<String, String>   mMapFromTempToFinalCommit = new HashMap<>(256);
	private final        BiMap<String, String> mFinalHeadToCommit        = HashBiMap.create();
	private final        BiMap<String, String> mFinalCommitToHead        = mFinalHeadToCommit.inverse();
	private              Path                  mTempRepoPath             = null;
	private              PrintWriter           mMappingFileWriter        = null;
	private              Path                  mFinalRepoPath            = null;

	@Parameter(names = "--source", description = "Url to the original git repository")
	private String mOriginalRepo = null;

	@Parameter(names = "--tempRepo", description = "Path to the temporary repo.  If specified must not exist")
	private String mTemporaryRepo = null;

	@Parameter(names = "--finalRepo", description = "Path to the final repo.")
	private String mFinalRepo = null;

	@Parameter(names = "--useExistingRepo",
	           description = "Use this option if the destination repo already exists.  Avoid creating it")
	private boolean mUseExistingRepo = false;

	@Parameter(names = "--topFolder", description = "Folder that will become new top of the new "
	                                                + "repo")
	private String mNewTopDirectory = null;

	@Parameter(names = "--branches", description = "Comma separated branches to include in new repo",
	           variableArity = true)
	private List<String> mBranchesToCopy = null;

	@Parameter(names = "--mapFile", description = "Path to the file to store the mappings from " +
	                                              "original commit to new one")
	private String mMappingFile = null;

	@Parameter(names = "--exclude",
	           description = "Comma separated list of directories to exclude in new repo",
	           variableArity = true)
	private List<String> mDirectoriesToExclude = null;

	@Parameter(names = "--recoverFile", description = "File to get info to use if a failure occurred")
	private File mRecoveryFile = null;

	@Parameter(names = {"-h", "--help"}, description = "Displays help information", help = true)
	private boolean mHelpOnly = false;

	private String        mFinalCurrentHead        = null;
	private String        mTempLastCommitProcessed = null;
	private String        mFinalInitialCommit      = null;
	private RSyncOption[] mRSyncOptions            = null;

	public static
	void main(@Nonnull final String... args) throws CLIException {
		final Main cliTool = new Main();
		cliTool.run(args, gitCommand(), rsyncCommand(), cliTool);
	}

	public
	Path getFinalRepoPath() {
		return mFinalRepoPath;
	}

	public
	PrintWriter getMappingFileWriter() {
		return mMappingFileWriter;
	}

	public
	Path getTempRepoPath() {
		return mTempRepoPath;
	}

	private
	void createFinalRepo(@Nonnull final Path pathToNewRepo, @Nonnull final Collection<String> initialBranches) throws
	                                                                                                           CLIException {
		Objects.requireNonNull(pathToNewRepo);
		Objects.requireNonNull(initialBranches);
		reviewResult(gitCommand().init(pathToNewRepo));
		initialBranches.forEach(b -> {
			getLogger().debug("Creating initial branch \"{}\"", b);
			try {
				reviewResult(gitCommand().checkout(pathToNewRepo, createBranch, arguments(b)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	@Override
	protected
	void cleanUp() throws CLIException {
	}

	@Nonnull
	private
	String reviewResult(@Nonnull final CompletableFuture<ProcessResult> result) throws CLIException {
		try {
			Objects.requireNonNull(result);
			final ProcessResult processResult = result.get();
			if (processResult.getExitValue() != 0) {
				final String command = Arrays.stream(processResult.getArgs()).collect(joining("', '", "'", "'"));
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
	void filterBranch(@Nonnull final Collection<String> branchesToCopy,
	                  @Nonnull final String newTopDirectory) throws CLIException {
		Objects.requireNonNull(branchesToCopy);
		Objects.requireNonNull(newTopDirectory);
		getLogger().info("Filtering branch(s):{}",
		                 branchesToCopy.stream().map(s -> '"' + s + '"').collect(joining(", ")));
		reviewResult(gitCommand().filterBranch(getTempRepoPath(),
		                                       pruneEmpty, subdirectoryFilter(newTopDirectory),
		                                       arguments(branchesToCopy.stream())));
	}

	private
	void createNeededTempBranches(@Nonnull final Collection<String> branchesToCopy) throws CLIException {
		Objects.requireNonNull(branchesToCopy);
		getLogger().debug("Getting current branches");
		final Set<String> branchesToCreate = new HashSet<>(branchesToCopy);
		final String branchesInfo = reviewResult(gitCommand().branch(getTempRepoPath(),
		                                                             GitVerboseOption.verbose));
		linesInString(branchesInfo).map(s -> s.substring(1).trim())
		                           .map(getFirstWord())
		                           .forEach(branchesToCreate::remove);
		branchesToCreate.forEach(s -> {
			try {
				getLogger().debug("Creating temp branch \"{}\"", s);
				//noinspection HardcodedFileSeparator
				reviewResult(gitCommand().checkout(getTempRepoPath(), createBranch, arguments(s, "origin/" + s)));
			} catch (final CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private
	void removeOrigin() throws CLIException {
		getLogger().debug("Getting remotes");
		final String remoteResults = reviewResult(gitCommand().remote(getTempRepoPath(), GitVerboseOption
				.verbose));
		final Set<String> remotes = linesInString(remoteResults).map(getFirstWord()).collect(toSet());
		remotes.stream().forEach(s -> {
			getLogger().debug("Removing remote \"{}" + '\"', s);
			try {
				reviewResult(gitCommand().remote(getTempRepoPath(), remove, arguments(s)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private
	ImmutableList<String> getCommitListInReverse(@Nonnull final Collection<String> branchesToCopy) throws
	                                                                                               CLIException {
		Objects.requireNonNull(branchesToCopy);
		final Set<String> relevantCommits = branchesToCopy.stream().flatMap(s -> {
			try {
				return linesInString(reviewResult(gitCommand().log(getTempRepoPath(),
				                                                   format("%h"),
				                                                   arguments(s)))).map(getFirstWord());
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		}).collect(toSet());
		try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get("/tmp/relevantCommits.txt"),
		                                                                        StandardOpenOption.TRUNCATE_EXISTING,
		                                                                        StandardOpenOption.CREATE)
		)) {
			relevantCommits.forEach(writer::println);
		} catch (final IOException e) {
			getLogger().error("Can't output commits", e);
			throw new CLIException(e);
		}

		final String allCommits = reviewResult(gitCommand().log(getTempRepoPath(), format("%h"),
		                                                        all));
		try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get("/tmp/allCommits.txt"),
		                                                                        StandardOpenOption.TRUNCATE_EXISTING,
		                                                                        StandardOpenOption.CREATE)
		)) {
			linesInString(allCommits).map(getFirstWord())
			                         .filter(relevantCommits::contains)
			                         .collect(immutableListCollector())
			                         .reverse().forEach(writer::println);
		} catch (final IOException e) {
			getLogger().error("Can't output commits", e);
			throw new CLIException(e);
		}
		return linesInString(allCommits).map(getFirstWord())
		                                .filter(relevantCommits::contains)
		                                .collect(immutableListCollector())
		                                .reverse();
	}

	@Override
	public
	void validate() throws CLIException {
		try {
			if (mRecoveryFile != null)
				validateRecoveryOptions();
			else
				validateNonRecoveryOptions();
		} catch (final IOException e) {
			throw new CLIInvalidArgumentException("Can't setup tool", e);
		}
	}

	private
	void reportConfigurationError(@Nonnull final String msg) throws CLIInvalidArgumentException {
		final StringBuilder sb = new StringBuilder(256);
		displayHelp(sb);
		getLogger().info(sb.toString());
		throw new CLIInvalidArgumentException(msg);
	}

	private
	void validateNonRecoveryOptions() throws CLIException, IOException {
		if (mOriginalRepo == null)
			reportConfigurationError("'--source' is required");
		if (mFinalRepo == null)
			reportConfigurationError("'--finalRepo' is required");
		if (mNewTopDirectory == null
		    && (mDirectoriesToExclude == null || mDirectoriesToExclude.isEmpty())
		    && !mUseExistingRepo)
			reportConfigurationError("One of '--topFolder', '--useExistingRepo' or '--exclude' is required");
		if (mNewTopDirectory != null && mDirectoriesToExclude != null && mDirectoriesToExclude.size() > 0) {
			reportConfigurationError(
					"Only one of \'--topFolder\' and \'--exclude\' option is supported");
		}
		if (mBranchesToCopy == null || mBranchesToCopy.isEmpty())
			reportConfigurationError("'--branches' is required");
		if (mMappingFile == null)
			reportConfigurationError("'--mapFile' is required");
		final Path tempRepo;
		if (mTemporaryRepo == null) {
			tempRepo = Files.createTempDirectory("GitSplitterTempRepo");
		}
		else {
			tempRepo = Paths.get(mTemporaryRepo);
			if (Files.exists(tempRepo)) {
				reportConfigurationError("Temporary path \"" + mTemporaryRepo + "\" should not " +
				                         "exist");
			}
		}
		mTempRepoPath = Files.createDirectories(tempRepo).normalize();
		if (StringUtils.isEmpty(mFinalRepo)) {
			reportConfigurationError("Final repo argument can't be empty");
		}
		final Path finalRepoPath = Paths.get(mFinalRepo);
		if (mUseExistingRepo) {
			if (!Files.exists(finalRepoPath))
				reportConfigurationError("Final repo \"" + mFinalRepo + "\" not found");
			mFinalRepoPath = finalRepoPath;
			final ImmutableSet<String> existingBranches = linesInString(reviewResult(gitCommand().branch
					(mFinalRepoPath))).map(s -> s.substring(1).trim()).collect(immutableSetCollector());
			mBranchesToCopy.stream().filter(existingBranches::contains).forEach(b -> CLIInvalidArgumentException
					.rtWrap(
							"Branch to copy \"" + b + "\" already exists in existing repository"));

		}
		else if (Files.exists(finalRepoPath))
			reportConfigurationError("Final repo \"" + mFinalRepo + "\" should not exist");
		else
			mFinalRepoPath = Files.createDirectories(finalRepoPath);
		final Path mappingFilePath = Paths.get(mMappingFile);
		if (Files.exists(mappingFilePath)) {
			reportConfigurationError("Mapping file \"" + mMappingFile + "\" should not exist");
		}
		mMappingFileWriter = new PrintWriter(Files.newBufferedWriter(mappingFilePath,
		                                                             StandardOpenOption.CREATE_NEW));
	}

	private
	void validateRecoveryOptions() throws CLIInvalidArgumentException, IOException {
		if (mOriginalRepo != null)
			reportConfigurationError("Only one of '--source' and '--recoverFile' can be "
			                         + "specified");
		if (mTemporaryRepo != null)
			reportConfigurationError("Only one of '--tempRepo' and '--recoverFile' can be "
			                         + "specified");
		if (mFinalRepo != null)
			reportConfigurationError("Only one of '--finalRepo' and '--recoverFile' can be "
			                         + "specified");
		if (mNewTopDirectory != null)
			reportConfigurationError("Only one of '--topFolder' and '--recoverFile' can be "
			                         + "specified");
		if (mBranchesToCopy != null && mBranchesToCopy.size() > 0)
			reportConfigurationError("Only one of '--branches' and '--recoverFile' can be "
			                         + "specified");
		if (mMappingFile != null)
			reportConfigurationError("Only one of '--mapFile' and '--recoverFile' can be "
			                         + "specified");
		readRecoveryFile();
	}

	private
	void readRecoveryFile() throws IOException {
		try (BufferedReader rdr = new BufferedReader(new FileReader(mRecoveryFile))) {
			rdr.lines().forEach(line -> {
				line = line.trim();
				if (!StringUtils.isEmpty(line)) {
					final String[] parts = line.split(":");
					if (parts.length == 2) {
						switch (parts[0]) {
							case "TempRepo":
								mTempRepoPath = Paths.get(parts[1]);
								if (!Files.exists(mTempRepoPath) && !Files.isDirectory(mTempRepoPath))
									throw CLIInvalidArgumentException.rtWrap("Temp repo not found");
								break;
							case "FinalRepo":
								mFinalRepoPath = Paths.get(parts[1]);
								if (!Files.exists(mFinalRepoPath) && !Files.isDirectory(mFinalRepoPath))
									throw CLIInvalidArgumentException.rtWrap("Final repo not found");
								break;
							case "Branches":
								mBranchesToCopy = Arrays.stream(parts[1].trim().split(",")).collect(
										immutableListCollector());
								if (mBranchesToCopy.isEmpty())
									throw CLIInvalidArgumentException.rtWrap("Branches not found");
								break;
							case "MappingFile":
								mMappingFile = parts[1].trim();
								final Path mappingFilePath = Paths.get(mMappingFile);
								if (!Files.exists(mappingFilePath)) {
									throw CLIInvalidArgumentException.rtWrap("Mapping file \""
									                                         + mMappingFile
									                                         + "\" not found");
								}
								try {
									try (final BufferedReader mapRdr = new BufferedReader(new FileReader(mMappingFile)
									)) {
										mMapFromTempToFinalCommit.putAll(mapRdr.lines().map(l -> {
											final String[] p = FunctionUtils.SPACES_PATTERN.split(l);
											if (p.length != 2)
												throw CLIInvalidArgumentException.rtWrap("Invalid mapping entry \""
												                                         + l
												                                         + '"');
											return new Tuple2<>(p[0].trim(), p[1].trim());
										}).collect(toMap(Tuple2::get_1, Tuple2::get_2)));
									}
									mMappingFileWriter = new PrintWriter(Files.newBufferedWriter(mappingFilePath,
									                                                             StandardOpenOption
											                                                             .APPEND));
								} catch (final IOException e) {
									throw CLIInvalidArgumentException.rtWrap("Can't open mapping file", e);
								}
								break;
							case "FinalInitial":
								mFinalInitialCommit = parts[1].trim();
								break;
							case "Exclude":
								mDirectoriesToExclude = Arrays.stream(parts[1].trim().split(",")).collect(
										immutableListCollector());
								if (mDirectoriesToExclude.isEmpty())
									throw CLIInvalidArgumentException.rtWrap("Directories to exclude not found");
								break;
							default:
								getLogger().error("Ignored invalid line in recovery file \"{}\"", line);
								break;
						}
					}
					else
						getLogger().error("Ignored invalid line in recovery file \"{}\"", line);
				}
			});
		}
	}

	@Override
	protected
	void execute() throws CLIException {
		try {
			if (mRecoveryFile == null) {
				getLogger().info("Cloning original repo into temporary one at \"{}\"", getTempRepoPath());
				reviewResult(gitCommand().cloneRepo(mOriginalRepo, getTempRepoPath()));
				createNeededTempBranches(mBranchesToCopy);
				if (mNewTopDirectory != null)
					filterBranch(mBranchesToCopy, mNewTopDirectory);
				removeOrigin();
				if (mUseExistingRepo)
					createMissingBranchesInFinalRepo(getFinalRepoPath(), mBranchesToCopy);
				else
					createFinalRepo(getFinalRepoPath(), mBranchesToCopy);
			}
			else
				populateExistingFinalBranches();
			final ImmutableList<String> commitList = getCommitListInReverse(mBranchesToCopy);
			commitList.stream().forEach(this::copyCommit);
			defineFinalBranches();
			deleteTemporaryRepo();
		} catch (final Throwable e) {
			final List<Throwable> exceptions = Throwables.getCausalChain(e);
			exceptions.stream().forEach(ex -> getLogger().error("Exception reported:", ex));
			createRecoveryFile();
		} finally {
			final PrintWriter writer = getMappingFileWriter();
			if (writer != null)
				writer.close();
		}
	}

	private
	void createMissingBranchesInFinalRepo(@Nonnull final Path repoPath, @Nonnull final List<String> branches) throws
	                                                                                                          CLIException {
		Objects.requireNonNull(repoPath);
		Objects.requireNonNull(branches);
		final ImmutableSet<String> existingBranches = linesInString(reviewResult(gitCommand().branch(repoPath))).map(s -> s
				.substring(
						1).trim()).collect(immutableSetCollector());
		branches.stream()
		        .filter(s -> !existingBranches.contains(s)).forEach(b -> {
			getLogger().debug("Creating initial branch \"{}\"", b);
			try {
				reviewResult(gitCommand().checkout(repoPath, createBranch, arguments(b)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	private
	void populateExistingFinalBranches() throws CLIException {
		final String branchesInfo = reviewResult(gitCommand().branch(getFinalRepoPath(),
		                                                             GitVerboseOption.verbose));
		final ImmutableList<String> existingBranches = linesInString(branchesInfo).map(s -> s.substring(1).trim())
		                                                                          .map(getFirstWord())
		                                                                          .collect(immutableListCollector());
		existingBranches.stream().forEach(b -> {
			try {
				reviewResult(gitCommand().checkout(getFinalRepoPath(), arguments(b)));
				final String currentCommit = getCurrentCommit(getFinalRepoPath());
				final boolean foundIt = mMapFromTempToFinalCommit.values().parallelStream().anyMatch(c -> c.equals(
						currentCommit));
				if (!foundIt)
					throw CLIException.rtWrap("Can't find branch HEAD commit");
				mFinalHeadToCommit.put(b, currentCommit);
			} catch (final CLIException e) {
				Throwables.propagate(e);
			}
		});
	}

	private
	void createRecoveryFile() throws CLIException {
		try {
			final Path recoveryFile = Files.createTempFile("GitSplitterRecoveryFile", ".txt");
			final StringBuilder sb = new StringBuilder(256);
			sb.append("TempRepo:");
			sb.append(mTempRepoPath.toString());
			sb.append("\nFinalRepo:");
			sb.append(mFinalRepoPath);
			sb.append("\nBranches:");
			sb.append(mBranchesToCopy.stream().collect(joining(",")));
			sb.append("\nMappingFile:");
			sb.append(mMappingFile);
			if (mFinalInitialCommit != null) {
				sb.append("\nFinalInitial:");
				sb.append(mFinalInitialCommit);
			}
			if (mDirectoriesToExclude != null && !mDirectoriesToExclude.isEmpty()) {
				sb.append("\nExclude:");
				sb.append(mDirectoriesToExclude.stream().collect(joining(",")));
			}
			FileUtils.writeStringToFile(recoveryFile.toFile(), sb.toString());
			getLogger().info("Recovery file: \"{}\"", recoveryFile.toString());
		} catch (final IOException ex) {
			throw new CLIException("Can't create recovery file", ex);
		}
	}

	private
	void deleteTemporaryRepo() throws CLIException {
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

	@Override
	protected
	boolean shouldRun() {
		return !mHelpOnly;
	}

	private
	void copyCommit(@Nonnull final String commit) {
		try {
			if (mRecoveryFile != null && mMapFromTempToFinalCommit.containsKey(commit))
				return;
			reviewResult(gitCommand().checkout(getTempRepoPath(), arguments(commit)));
			final CommitInfo commitInfo = gitCommand().getCommitInfo(getTempRepoPath(), commit).get();
			getLogger().info("Processing commit \"{}\":{} committed at {} - \"{}\"", commit, commitInfo.getAuthor(),
			                 commitInfo.getDate(), commitInfo.getComment());
			if (commitInfo.isMerge()) {
				doMerge(commit, commitInfo);
			}
			else {
				if (commitInfo.isRoot()) {
					prepareFinalBranch(getFinalRepoPath(), mFinalInitialCommit);
				}
				else if (mTempLastCommitProcessed == null || !mTempLastCommitProcessed.equals(commitInfo.getParents()
				                                                                                        .get(0))) {
					prepareFinalBranch(getFinalRepoPath(),
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
				copyFilesAndCommit(commit, commitInfo);
			}
			final String currentFinalCommit = getFinalCurrentCommit();
			getLogger().debug("Adding mapping from \"{}\" to commit \"{}\"", mFinalCurrentHead, currentFinalCommit);
			mFinalHeadToCommit.put(mFinalCurrentHead, currentFinalCommit);
			mTempLastCommitProcessed = commit;
			mMapFromTempToFinalCommit.put(commit, currentFinalCommit);
			if (mFinalInitialCommit == null) {
				mFinalInitialCommit = currentFinalCommit;
			}
			getMappingFileWriter().println(commit + '\t' + currentFinalCommit);
		} catch (final InterruptedException | ExecutionException | CLIException e) {
			getLogger().error("Failed processing commit \"{}\"", commit);
			throw Throwables.propagate(e);
		}
	}

	private
	void copyFilesAndCommit(@Nonnull final String commit,
	                        @Nonnull final CommitInfo commitInfo) throws CLIException {
		String source = getTempRepoPath().toString();
		if (!(source.length() > 0 && source.charAt(source.length() - 1) == '/')) {
			source += '/';
		}
		String destination = getFinalRepoPath().toString();
		if (!(destination.length() > 0 && destination.charAt(destination.length() - 1) == '/')) {
			destination += '/';
		}
		getLogger().debug("Copying from \"{}\" to \"{}\"", source, destination);
		reviewResult(rsyncCommand().sync(source, destination, getRSyncOptions()));
		final String status = reviewResult(gitCommand().status(getFinalRepoPath(), shortStatus));
		if (StringUtils.isEmpty(status)) {
			getLogger().info("Commit \"{}\" has no changes.  Creating a DUMMY_FILE.txt", commit);
			try {
				final File dummyFile = new File(mFinalRepoPath.toFile(), "DUMMY_FILE.txt");
				FileUtils.writeStringToFile(dummyFile, "Commit:" + commit);
			} catch (final IOException e) {
				throw new CLIException(e);
			}
		}
		reviewResult(gitCommand().add(getFinalRepoPath(), allFullTree));
		final String commitResult = reviewResult(gitCommand().commit(getFinalRepoPath(),
		                                                             author(commitInfo.getAuthor()),
		                                                             date(commitInfo.getDate()),
		                                                             message(commitInfo.getComment())));
		getLogger().debug("Commit result: \"{}\"", commitResult);
	}

	private
	void defineFinalBranches() throws CLIException {
		mBranchesToCopy.forEach(b -> {
			try {
				final String originalCommit = getFirstWord().apply(reviewResult(gitCommand().log(getTempRepoPath(),
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
				reviewResult(gitCommand().checkout(getFinalRepoPath(), createBranch, arguments(b,
				                                                                               existingHead
				                                                                               == null ?
				                                                                               newCommit :
				                                                                               existingHead)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalHeadToCommit.keySet().forEach(b -> {
			getLogger().debug("Removing temporary branch \"{}\".", b);
			try {
				reviewResult(gitCommand().branch(getFinalRepoPath(), forceDelete, arguments(b)));
			} catch (CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		getLogger().debug("Running GC...");
		reviewResult(gitCommand().gc(getFinalRepoPath(), aggressive, pruneAll));
	}

	private
	void doMerge(@Nonnull final String commit, final CommitInfo commitInfo) throws CLIException {
		final ImmutableList<String> headsToMerge = commitInfo.getParents()
		                                                     .stream()
		                                                     .map(mMapFromTempToFinalCommit::get)
		                                                     .map(finalCommit -> {
			                                                     try {
				                                                     prepareFinalBranch(mFinalRepoPath, finalCommit);
			                                                     } catch (final CLIException e) {
				                                                     Throwables.propagate(e);
			                                                     }
			                                                     return mFinalCurrentHead;
		                                                     })
		                                                     .collect(immutableListCollector());
		if (headsToMerge.size() < 2)
			throw new CLIException("Can't find the two commits to merge");

		final Optional<String> survivorHeadOption = headsToMerge.stream().min(Comparator.naturalOrder());
		if (!survivorHeadOption.isPresent()) {
			throw new CLIException("Can't find head to merge");
		}
		final String survivorHead = survivorHeadOption.get();
		final ImmutableList<String> otherHeads =
				headsToMerge.stream().filter(s -> !survivorHead.equals(s)).collect(immutableListCollector());
		getLogger().debug("Checkout \"{}\" in final repo", survivorHead);
		reviewResult(gitCommand().checkout(getFinalRepoPath(), arguments(survivorHead)));
		try {
			getLogger().debug("Issuing the merge command to \"{}\" from {}",
			                  survivorHead,
			                  otherHeads.stream().map(s -> '"' + s + '"').collect(joining(", ")));
			reviewResult(gitCommand().merge(getFinalRepoPath(), message(commitInfo.getComment()), arguments(otherHeads
					                                                                                                .stream())));
		} catch (final CLIException e) {
			if (e.getMessage().contains("Merge conflict") || e.getMessage().contains("Automatic merge failed; fix "
			                                                                         + "conflicts and then commit the "
			                                                                         + "result")) {
				getLogger().debug("Found merge conflict.  Issuing a copy and commit");
				copyFilesAndCommit(commit, commitInfo);
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
				reviewResult(gitCommand().branch(getFinalRepoPath(), GitBranchDeleteOption.deleteBranch, arguments
						(b)));
			} catch (final CLIException e) {
				throw Throwables.propagate(e);
			}
		});
		mFinalCurrentHead = survivorHead;
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
		return getCurrentCommit(getFinalRepoPath());
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
		if (baseCommit == null) {
			mFinalCurrentHead = getNewHead();
			reviewResult(gitCommand().checkout(finalPath, createBranch, arguments(mFinalCurrentHead)));
		}
		else {
			mFinalCurrentHead = mFinalCommitToHead.get(baseCommit);
			if (mFinalCurrentHead == null) {
				mFinalCurrentHead = getNewHead();
				reviewResult(gitCommand().checkout(finalPath, createBranch, arguments(mFinalCurrentHead, baseCommit)));
			}
			else
				reviewResult(gitCommand().checkout(finalPath, arguments(mFinalCurrentHead)));
		}
	}

	public
	RSyncOption[] getRSyncOptions() {
		if (mRSyncOptions == null) {
			Stream<RSyncOption> options = Stream.of(archive,
			                                        delete,
			                                        exclude(".git"));
			if (mDirectoriesToExclude != null) {
				options = Stream.concat(options, mDirectoriesToExclude.stream().map(
						RSyncExcludeOption::exclude));
			}
			final ImmutableList<RSyncOption> allOptions = options.collect(immutableListCollector());
			mRSyncOptions = allOptions.toArray(new RSyncOption[allOptions.size()]);
		}
		//noinspection ReturnOfCollectionOrArrayField
		return mRSyncOptions;
	}
}
