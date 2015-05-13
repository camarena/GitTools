package org.camarena.tools.oscommands.git;

import com.beust.jcommander.Parameter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.camarena.tools.CLIException;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.Configuration;
import org.camarena.tools.FunctionUtils;
import org.camarena.tools.oscommands.OSCommand;
import org.camarena.tools.oscommands.OSCommandOption;
import org.camarena.tools.oscommands.ProcessResult;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.camarena.tools.FunctionUtils.getFirstWord;
import static org.camarena.tools.StreamUtils.immutableListCollector;
import static org.camarena.tools.StreamUtils.linesInString;
import static org.camarena.tools.oscommands.git.GitArgument.arguments;
import static org.camarena.tools.oscommands.git.GitLogAbbrevCommitOption.abbrev;
import static org.camarena.tools.oscommands.git.GitLogMaxCountOption.maxCount;
import static org.camarena.tools.oscommands.git.GitLogPrettyOption.pretty;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings("HardcodedFileSeparator")
public
class GitCommand extends OSCommand implements Configuration {
	private static final String     LINE_SEPARATOR = System.getProperty("line.separator");
	private static final GitCommand mgOurInstance  = new GitCommand();
	@SuppressWarnings("unused")
	@Parameter(names = "--git", description = "Path to the git command", required = false)
	private              String     mGitCommandX   = "/usr/local/bin/git";

	private
	GitCommand() {
		super("git");
	}

	public static
	GitCommand gitCommand() {
		return mgOurInstance;
	}

	public
	CompletableFuture<ProcessResult> gc(@Nonnull final Path pathToRepo,
	                                    @Nonnull final GitGCOption... options) throws CLIException {
		return common("gc", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> filterBranch(@Nonnull final Path pathToRepo,
	                                              @Nonnull final GitFilterBranchOption... options) throws
	                                                                                               CLIException {
		return common("filter-branch", pathToRepo, options);
	}

	private
	CompletableFuture<ProcessResult> common(@Nonnull final String command, @Nonnull final Path pathToRepo,
	                                        @Nonnull final OSCommandOption... options) throws CLIException {
		Objects.requireNonNull(command);
		Objects.requireNonNull(pathToRepo);
		Objects.requireNonNull(options);
		return runOsCommand(Optional.of(pathToRepo.toFile()), Stream.concat(Stream.of(command),
		                                                                    Arrays.stream(options)
		                                                                          .flatMap
				                                                                          (OSCommandOption::asStream)));
	}

	public
	CompletableFuture<ProcessResult> merge(@Nonnull final Path pathToRepo,
	                                       @Nonnull final GitMergeOption... options) throws CLIException {
		return common("merge", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> remote(@Nonnull final Path pathToRepo,
	                                        @Nonnull final GitRemoteOption... options) throws CLIException {
		return common("remote", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> branch(@Nonnull final Path pathToRepo,
	                                        @Nonnull final GitBranchOption... options) throws CLIException {

		return common("branch", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> checkout(@Nonnull final Path pathToRepo,
	                                          @Nonnull final GitCheckoutOption... options) throws
	                                                                                       CLIException {
		return common("checkout", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> cloneRepo(@Nonnull final String originalRepo,
	                                           @Nonnull final Path pathToNewRepo) throws CLIException {
		Objects.requireNonNull(originalRepo);
		Objects.requireNonNull(pathToNewRepo);
		return runOsCommand(Stream.of("clone", originalRepo, pathToNewRepo.toString()));
	}

	public
	CompletableFuture<ProcessResult> init(@Nonnull final Path pathToNewRepo) throws CLIException {
		Objects.requireNonNull(pathToNewRepo);
		return runOsCommand(Optional.of(pathToNewRepo.toFile()), Stream.of("init"));
	}

	public
	CompletableFuture<ProcessResult> log(@Nonnull final Path pathToRepo, @Nonnull final GitLogOption... options) throws
	                                                                                                             CLIException {
		return common("log", pathToRepo, options);
	}

	public
	CompletableFuture<CommitInfo> getCommitInfo(@Nonnull final Path pathToRepo, @Nonnull final String commit) throws
	                                                                                                          CLIException {
		Objects.requireNonNull(pathToRepo);
		Objects.requireNonNull(commit);
		return log(pathToRepo, maxCount(1), arguments(commit)).thenApply(pr -> {
			try {
				final String resultOfLog = pr.getStdOut();
				final ImmutableList<String> lines = linesInString(resultOfLog).collect(immutableListCollector());
				if (lines.size() < 4) {
					throw new CLIException("Can't parse result from log -1.\n" + resultOfLog);
				}
				if (!lines.get(0).startsWith("commit ")) {
					throw new CLIException("Can't parse result from log -1.\n" + resultOfLog);
				}
				int inx = 1;
				final ImmutableList<String> parents;
				if (lines.get(inx).startsWith("Merge:")) {
					parents = Arrays.stream(FunctionUtils.SPACES_PATTERN.split(lines.get(inx)
					                                                                .substring("Merge:".length())
					                                                                .trim()))
					                .collect(immutableListCollector());
					inx++;
				}
				else {
					final Optional<String> optionalParent = getParentOfNonMergeCommit(pathToRepo, commit).get();
					parents = optionalParent
							.map(ImmutableList::of)
							.orElse(ImmutableList.of());
				}
				if (!lines.get(inx).startsWith("Author: ")) {
					throw new CLIException("Can't parse result from log -1.\n" + resultOfLog);
				}
				final String author = lines.get(inx).substring("Author: ".length()).trim();
				inx++;
				if (!lines.get(inx).startsWith("Date: ")) {
					throw new CLIException("Can't parse result from log -1.\n" + resultOfLog);
				}
				final String date = lines.get(inx).substring("Date: ".length()).trim();
				inx++;
				if (!StringUtils.isEmpty(lines.get(inx).trim())) {
					throw new CLIException("Can't parse result from log -1.\n" + resultOfLog);
				}
				inx++;
				final String comment = lines.stream().skip(inx).map(String::trim).collect(joining(LINE_SEPARATOR));
				return new CommitInfo(author, date, comment, parents);
			} catch (InterruptedException | ExecutionException | CLIException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	@Override
	public
	void validate() throws CLIInvalidArgumentException {
		setPathToCommand(mGitCommandX);
	}

	@Nonnull
	private
	CompletableFuture<Optional<String>> getParentOfNonMergeCommit(@Nonnull final Path pathToRepo,
	                                                              @Nonnull final String commit) throws
	                                                                                            CLIException {
		return log(pathToRepo, abbrev, pretty("oneline"), maxCount(1), arguments(commit
		                                                                         + '^')).thenApply(pr -> {
			final String resultOfLog = pr.getStdOut().trim();
			if (StringUtils.isEmpty(resultOfLog))
				return Optional.empty();
			return Optional.of(getFirstWord().apply(resultOfLog));
		});
	}

	public
	CompletableFuture<ProcessResult> status(@Nonnull final Path pathToRepo,
	                                        @Nonnull final GitStatusOption... options) throws
	                                                                                   CLIException {
		return common("status", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> commit(@Nonnull final Path pathToRepo,
	                                        @Nonnull final GitCommitOption... options) throws CLIException {

		return common("commit", pathToRepo, options);
	}

	public
	CompletableFuture<ProcessResult> add(@Nonnull final Path pathToRepo, @Nonnull final GitAddOption... options) throws
	                                                                                                 CLIException {
		return common("add", pathToRepo, options);
	}
}
