package org.camarena.tools.oscommands.rsync;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class RSyncDeleteOption extends SimpleOSCommandOption implements RSyncOption{
	public static final RSyncDeleteOption delete = new RSyncDeleteOption();

	private
	RSyncDeleteOption() {
		super("--delete");
	}
}
