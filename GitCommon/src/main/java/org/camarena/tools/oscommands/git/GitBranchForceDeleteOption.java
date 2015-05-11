package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitBranchForceDeleteOption extends SimpleOSCommandOption implements GitBranchOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitBranchForceDeleteOption forceDelete = new GitBranchForceDeleteOption();

	private
	GitBranchForceDeleteOption() {
		super("-D");
	}
}
