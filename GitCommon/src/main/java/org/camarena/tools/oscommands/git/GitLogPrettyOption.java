package org.camarena.tools.oscommands.git;

import com.beust.jcommander.internal.Nullable;
import com.google.common.collect.ImmutableSet;
import org.camarena.tools.CLIInvalidArgumentException;
import org.camarena.tools.oscommands.StringOSCommandOption;

import java.util.Optional;

/**
 * Limit the number of commits to output.
 *
 * @author Hermán de J. Camarena R.
 */
public
class GitLogPrettyOption extends StringOSCommandOption implements GitLogOption {
	private static ImmutableSet<String> mgValues = ImmutableSet.copyOf(new String[]{"oneline",
	                                                                                "short",
	                                                                                "medium",
	                                                                                "full",
	                                                                                "fuller",
	                                                                                "email",
	                                                                                "raw"});

	public
	GitLogPrettyOption(@Nullable final String value) throws CLIInvalidArgumentException {
		super("--pretty", Optional.ofNullable(value));
		if (value != null) {
			if (!value.startsWith("format:") && !mgValues.contains(value))
				throw new CLIInvalidArgumentException("Invalid value \"" + value + "\" for --pretty");
		}
	}

	public static
	GitLogPrettyOption pretty(@Nullable final String value) throws CLIInvalidArgumentException {
		return new GitLogPrettyOption(value);
	}
}
