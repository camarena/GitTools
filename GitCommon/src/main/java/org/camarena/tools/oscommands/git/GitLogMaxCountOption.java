package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.IntOSCommandOption;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public class GitLogMaxCountOption extends IntOSCommandOption implements GitLogOption {
    public
    GitLogMaxCountOption(final int value) {
        super("--max-count", value);
    }

    public static
    GitLogMaxCountOption maxCount(final int value) {
        return new GitLogMaxCountOption(value);
    }
}
