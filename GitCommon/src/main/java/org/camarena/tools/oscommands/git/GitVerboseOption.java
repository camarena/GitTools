package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitVerboseOption extends SimpleOSCommandOption implements GitBranchOption, GitRemoteOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitVerboseOption verbose = new GitVerboseOption();

	private
	GitVerboseOption() {
		super("-v");
	}
}
