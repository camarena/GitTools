package org.camarena.tools.oscommands.rsync;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class RSyncVerboseOption extends SimpleOSCommandOption implements RSyncOption{
	public static final RSyncVerboseOption verbose = new RSyncVerboseOption();

	private
	RSyncVerboseOption() {
		super("--verbose");
	}
}
