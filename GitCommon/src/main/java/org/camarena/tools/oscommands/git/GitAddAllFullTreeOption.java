package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitAddAllFullTreeOption extends SimpleOSCommandOption implements GitAddOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitAddAllFullTreeOption allFullTree = new GitAddAllFullTreeOption();

	private
	GitAddAllFullTreeOption() {
		super("-A");
	}
}
