package org.camarena.tools.oscommands;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.camarena.tools.CLIException;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * @author Hermán de J. Camarena R.
 */
public
class OSCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger(OSCommand.class);

	@Nonnull
	private final String mCommandName;
	private Optional<Path> mPathToCommand = Optional.empty();

	public
	OSCommand(@Nonnull final String commandName) {
		Objects.requireNonNull(commandName);
		mCommandName = commandName;
	}

	public
	void setPathToCommand(@Nullable final String pathToCommand) throws CLIInvalidArgumentException {
		if (pathToCommand == null)
			mPathToCommand = Optional.empty();
		else {
			final Path gitPath = Paths.get(pathToCommand);
			if (!Files.isExecutable(gitPath)) {
				throw new CLIInvalidArgumentException("Command \"" +
				                                      pathToCommand +
				                                      "\" does not exist or is not executable");
			}
			mPathToCommand = Optional.of(gitPath);
		}
	}

	/**
	 * {@link Logger} for this class.
	 *
	 * @return {@link Logger} for this class
	 */
	protected
	Logger getLogger() {
		return LOGGER;
	}

	protected
	CompletableFuture<ProcessResult> runOsCommand(@Nonnull final Stream<String> argsAndOptions)
			throws CLIException {
		return runOsCommand(Optional.empty(), argsAndOptions);
	}

	protected
	CompletableFuture<ProcessResult> runOsCommand(@Nonnull final Optional<File> workingDirectory,
	                                              @Nonnull final Stream<String> argsAndOptions)
			throws CLIException {
		Objects.requireNonNull(workingDirectory);
		Objects.requireNonNull(argsAndOptions);
		final String[] args = argsToProcess(argsAndOptions);
		return CompletableFuture.supplyAsync(() -> {
			try {
				final ProcessBuilder processBuilder = new ProcessBuilder(args);
				workingDirectory.ifPresent(processBuilder::directory);
				final Process process = processBuilder.start();
				// Read stdIn and stdOut in a different thread to avoid blocking due to buffering
				final CompletableFuture<byte[]> stdOut = CompletableFuture.supplyAsync(() -> {
					try {
						return ByteStreams.toByteArray(process.getInputStream());
					} catch (IOException e) {
						throw Throwables.propagate(e);
					}
				});
				final CompletableFuture<byte[]> stdErr = CompletableFuture.supplyAsync(() -> {
					try {
						return ByteStreams.toByteArray(process.getErrorStream());
					} catch (IOException e) {
						throw Throwables.propagate(e);
					}
				});
				process.waitFor();
				final int exitValue = process.exitValue();
				return new ProcessResult(exitValue, stdOut.get(), stdErr.get(), workingDirectory, args);
			} catch (IOException | InterruptedException | ExecutionException e) {
				throw Throwables.propagate(e);
			}
		});
	}

	protected
	String osCommand() throws CLIException {
		return mPathToCommand.orElseThrow(() -> new CLIException("No \""
		                                                         + mCommandName
		                                                         + "\" executable is configured")).toString();
	}

	private
	String[] argsToProcess(@Nonnull final Stream<String> params) {
		try {
			final ImmutableList<String> argsAsList = Stream.concat(Stream.of(osCommand()), params)
			                                               .collect(StreamUtils.immutableListCollector());
			return argsAsList.toArray(new String[argsAsList.size()]);
		} catch (final CLIException e) {
			throw Throwables.propagate(e);
		}
	}
}
