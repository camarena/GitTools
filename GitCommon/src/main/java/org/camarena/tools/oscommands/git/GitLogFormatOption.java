package org.camarena.tools.oscommands.git;

import com.beust.jcommander.internal.Nullable;
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
class GitLogFormatOption extends StringOSCommandOption implements GitLogOption {

	public
	GitLogFormatOption(@Nonnull final String value) {
		super("--format", Optional.ofNullable(value));
		Objects.requireNonNull(value);
	}

	public static
	GitLogFormatOption format(@Nullable final String value) {
		return new GitLogFormatOption(value);
	}
}
