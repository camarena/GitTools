package org.camarena.tools;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author Hermán de J. Camarena R.
 */
public
class FunctionUtils {
	public static final Pattern SPACES_PATTERN = Pattern.compile("\\p{Space}+");

	private
	FunctionUtils() {
	}

	public static
	Function<String, String> getFirstWord() {
		return getNWord(0);
	}

	public static
	Function<String, String> getNWord(final int n) {
		return s -> {
			final String[] parts = SPACES_PATTERN.split(s);
			return parts[n];
		};
	}

	public static
	Function<String, String> getSecondWord() {
		return getNWord(1);
	}

}
