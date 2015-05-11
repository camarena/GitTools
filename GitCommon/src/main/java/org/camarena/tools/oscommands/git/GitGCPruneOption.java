package org.camarena.tools.oscommands.git;

import com.beust.jcommander.internal.Nullable;
import org.camarena.tools.oscommands.StringOSCommandOption;

import java.util.Optional;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public
class GitGCPruneOption extends StringOSCommandOption implements GitGCOption {
	@SuppressWarnings("ConstantNamingConvention")
	public static final GitGCPruneOption pruneAll = new GitGCPruneOption("all");

	public
	GitGCPruneOption(@Nullable final String value) {
		super("--prune", Optional.ofNullable(value));
	}

	public static
	GitGCPruneOption prune(@Nullable final String value) {
		return new GitGCPruneOption(value);
	}
}
