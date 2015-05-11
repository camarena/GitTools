package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitFilterBranchPruneEmptyOption extends SimpleOSCommandOption implements GitFilterBranchOption{
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitFilterBranchPruneEmptyOption pruneEmpty = new GitFilterBranchPruneEmptyOption();

	private
	GitFilterBranchPruneEmptyOption() {
		super("--prune-empty");
	}
}
