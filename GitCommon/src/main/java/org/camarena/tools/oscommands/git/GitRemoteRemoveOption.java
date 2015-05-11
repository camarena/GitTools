package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitRemoteRemoveOption extends SimpleOSCommandOption implements GitRemoteOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitRemoteRemoveOption remove = new GitRemoteRemoveOption();

	private
	GitRemoteRemoveOption() {
		super("remove");
	}
}
