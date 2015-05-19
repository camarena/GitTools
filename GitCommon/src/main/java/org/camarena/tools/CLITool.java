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
public abstract
class CLITool {

	private static final Logger LOGGER = LoggerFactory.getLogger(CLITool.class);
	private JCommander mCommander;

	protected abstract
	void cleanUp() throws CLIException;

	protected
	void displayHelp(@Nonnull final JCommander commander) {
		Objects.requireNonNull(commander);
		final StringBuilder out = new StringBuilder(256);
		commander.usage(out);
		getLogger().info(out.toString());
	}

	protected abstract
	void execute() throws CLIException;

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
	void run(final String[] args, final Configuration... configurations) throws CLIException {
		mCommander = new JCommander();
		Arrays.stream(configurations).forEach(mCommander::addObject);
		try {
			mCommander.parse(args);
		} catch (final ParameterException e) {
			getLogger().error("Invalid configuration", e);
			displayHelp(mCommander);
			throw new CLIInvalidArgumentException("Can't recognize configuration. ", e);
		}
		if (shouldRun())
			Arrays.stream(configurations).forEach((configuration) -> {
				try {
					configuration.validate();
				} catch (final CLIException e) {
					throw Throwables.propagate(e);
				}
			});
		try {
			if (shouldRun())
				execute();
		} finally {
			cleanUp();
		}
	}

	protected abstract
	boolean shouldRun();

	protected
	void displayHelp(@Nonnull final StringBuilder out) {
		mCommander.usage(out);
	}

}
