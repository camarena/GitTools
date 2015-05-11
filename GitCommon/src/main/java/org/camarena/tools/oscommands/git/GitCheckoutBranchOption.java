package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitCheckoutBranchOption extends SimpleOSCommandOption implements GitCheckoutOption {
	public static final GitCheckoutBranchOption createBranch = new GitCheckoutBranchOption();

	private
	GitCheckoutBranchOption() {
		super("-b");
	}
}
