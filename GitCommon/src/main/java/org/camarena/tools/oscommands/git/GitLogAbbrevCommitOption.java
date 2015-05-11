package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.SimpleOSCommandOption;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public
class GitLogAbbrevCommitOption extends SimpleOSCommandOption implements GitLogOption {
	public static final GitLogAbbrevCommitOption abbrev = new GitLogAbbrevCommitOption();

	public
	GitLogAbbrevCommitOption() {
		super("--abbrev-commit");
	}
}
