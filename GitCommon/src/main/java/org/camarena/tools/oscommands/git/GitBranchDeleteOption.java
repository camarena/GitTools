package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitBranchDeleteOption extends SimpleOSCommandOption implements GitBranchOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitBranchDeleteOption deleteBranch = new GitBranchDeleteOption();

	private
	GitBranchDeleteOption() {
		super("-d");
	}
}
