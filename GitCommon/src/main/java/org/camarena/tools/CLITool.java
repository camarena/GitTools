package org.camarena.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author Hermán de J. Camarena R.
 */
public abstract class CLITool {

	protected abstract void cleanUp() throws CLIException;

	protected void displayHelp(@Nonnull final JCommander commander) {
		Objects.requireNonNull(commander);
		final StringBuilder out = new StringBuilder(256);
		commander.usage(out);
		getLogger().info(out.toString());
	}

	protected abstract void execute() throws CLIException;

	/**
	 * {@link Logger} for this class.
	 *
	 * @return {@link Logger} for this class
	 */
	protected Logger getLogger() {
		return LOGGER;
	}

	protected void run(final String[] args, final Configuration... configurations) throws CLIException {
		final JCommander commander = new JCommander();
		Arrays.stream(configurations).forEach(commander::addObject);
		try {
			commander.parse(args);
		} catch (final ParameterException e) {
			getLogger().error("Invalid configuration", e);
			displayHelp(commander);
			throw new CLIInvalidArgumentException("Can't recognize configuration. ", e);
		}
		Arrays.stream(configurations).forEach((configuration) -> {
			try {
				configuration.validate();
			} catch (final CLIInvalidArgumentException e) {
				throw Throwables.propagate(e);
			}
		});
		try {
			execute();
		} finally {
			cleanUp();
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(CLITool.class);

}
