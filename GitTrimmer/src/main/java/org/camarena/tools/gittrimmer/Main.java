package org.camarena.tools.gittrimmer;

import org.camarena.tools.CLIException;
import org.camarena.tools.CLITool;

import javax.annotation.Nonnull;

/**
 * @author Hermán de J. Camarena R.
 */
public
class Main extends CLITool {
	public static
	void main(@Nonnull final String... args) throws CLIException {
		new Main().run(args);
	}

	@Override
	protected
	void cleanUp() throws CLIException {
		// TODO: Implement this method or remove this comment if there is nothing to do

	}

	@Override
	protected
	void execute() throws CLIException {
		// TODO: Implement this method or remove this comment if there is nothing to do

	}
}
