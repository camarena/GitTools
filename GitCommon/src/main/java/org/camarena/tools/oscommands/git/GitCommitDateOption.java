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
class GitCommitDateOption extends StringOSCommandOption implements GitCommitOption {

	public
	GitCommitDateOption(@Nonnull final String value) {
		super("--date", Optional.ofNullable(value));
		Objects.requireNonNull(value);
	}

	public static
	GitCommitDateOption date(@Nonnull final String value) {
		return new GitCommitDateOption(value);
	}
}
