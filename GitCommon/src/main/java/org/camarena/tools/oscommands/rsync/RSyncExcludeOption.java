package org.camarena.tools.oscommands.rsync;

import org.camarena.tools.oscommands.StringOSCommandOption;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * 	exclude files matching PATTERN.
 *
 * @author Hermán de J. Camarena R.
 */
public
class RSyncExcludeOption extends StringOSCommandOption implements RSyncOption {
	public
	RSyncExcludeOption(@Nonnull final String pattern) {
		super("--exclude", Optional.ofNullable(pattern));
		Objects.requireNonNull(pattern);
	}

	public static
	RSyncExcludeOption exclude(@Nonnull final String pattern) {
		return new RSyncExcludeOption(pattern);
	}
}
