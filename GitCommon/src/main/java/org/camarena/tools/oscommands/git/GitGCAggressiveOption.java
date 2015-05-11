package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitGCAggressiveOption extends SimpleOSCommandOption implements GitGCOption {
	public static final GitGCAggressiveOption aggressive = new GitGCAggressiveOption();

	private
	GitGCAggressiveOption() {
		super("--aggressive");
	}
}
