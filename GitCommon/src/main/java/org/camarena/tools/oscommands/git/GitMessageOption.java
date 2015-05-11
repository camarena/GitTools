package org.camarena.tools.oscommands.git;

import org.camarena.tools.oscommands.StringOSCommandOption;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public
class GitMessageOption extends StringOSCommandOption implements GitCommitOption, GitMergeOption {

	public
	GitMessageOption(@Nonnull final String value) {
		super("-m", Optional.ofNullable(value), OutputKind.USE_TWO_ARGUMENTS);
		Objects.requireNonNull(value);
	}

	public static
	GitMessageOption message(@Nonnull final String value) {
		return new GitMessageOption(value);
	}
}
