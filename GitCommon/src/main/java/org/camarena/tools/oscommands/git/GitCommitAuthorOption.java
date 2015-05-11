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
class GitCommitAuthorOption extends StringOSCommandOption implements GitCommitOption {

	public
	GitCommitAuthorOption(@Nonnull final String value) {
		super("--author", Optional.ofNullable(value));
		Objects.requireNonNull(value);
	}

	public static
	GitCommitAuthorOption author(@Nonnull final String value) {
		return new GitCommitAuthorOption(value);
	}
}
