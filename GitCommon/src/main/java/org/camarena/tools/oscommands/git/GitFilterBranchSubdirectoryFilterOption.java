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
class GitFilterBranchSubdirectoryFilterOption extends StringOSCommandOption implements GitFilterBranchOption {

	public
	GitFilterBranchSubdirectoryFilterOption(@Nonnull final String value) {
		super("--subdirectory-filter", Optional.ofNullable(value),OutputKind.USE_TWO_ARGUMENTS);
		Objects.requireNonNull(value);
	}

	public static
	GitFilterBranchSubdirectoryFilterOption subdirectoryFilter(@Nonnull final String value) {
		return new GitFilterBranchSubdirectoryFilterOption(value);
	}
}
