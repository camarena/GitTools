package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public
class GitLogAllOption extends SimpleOSCommandOption implements GitLogOption {
	public static final GitLogAllOption all = new GitLogAllOption();

	public
	GitLogAllOption() {
		super("--all");
	}
}
