package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitStatusShortOption extends SimpleOSCommandOption implements GitStatusOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitStatusShortOption shortStatus = new GitStatusShortOption();

	private
	GitStatusShortOption() {
		super("--short");
	}
}
