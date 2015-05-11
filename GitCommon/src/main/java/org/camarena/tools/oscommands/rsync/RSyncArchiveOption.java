package org.camarena.tools.oscommands.rsync;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * @author Hermán de J. Camarena R.
 */
public
class RSyncArchiveOption extends SimpleOSCommandOption implements RSyncOption{
	public static final RSyncArchiveOption archive = new RSyncArchiveOption();

	private
	RSyncArchiveOption() {
		super("--archive");
	}
}
