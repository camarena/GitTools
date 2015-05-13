package org.camarena.tools.oscommands.rsync;

import com.beust.jcommander.Parameter;
import org.camarena.tools.CLIException;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.Configuration;
import org.camarena.tools.oscommands.OSCommand;
import org.camarena.tools.oscommands.OSCommandOption;
import org.camarena.tools.oscommands.ProcessResult;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings({"HardcodedFileSeparator", "EmptyClass"})
public
class RSyncCommand extends OSCommand implements Configuration {

	private static final RSyncCommand mgOurInstance  = new RSyncCommand();
	@SuppressWarnings("unused")
	@Parameter(names = "--rsync", description = "Path to the rsync command", required = false)
	private              String       mRSyncCommandX = "/usr/bin/rsync";

	private
	RSyncCommand() {
		super("rsync");
	}

	public static
	RSyncCommand rsyncCommand() {
		return mgOurInstance;
	}

	@Override
	public
	void validate() throws CLIInvalidArgumentException {
		setPathToCommand(mRSyncCommandX);
	}

	public
	CompletableFuture<ProcessResult> sync(@Nonnull final String source,
	                                      @Nonnull final String destination,
	                                      final RSyncOption... options) throws
	                                                                    CLIException {
		return runOsCommand(Stream.concat(Arrays.stream(options).map(OSCommandOption.class::cast).flatMap(
				                                  OSCommandOption::asStream),
		                                  Stream.of(source, destination)));
	}
}
