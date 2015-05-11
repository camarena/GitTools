package org.camarena.tools.oscommands;

import com.google.common.collect.ImmutableList.Builder;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * @author Hermán de J. Camarena R.
 */
public
interface OSCommandOption {
	void addToCommand(@Nonnull final Builder<String> builder);

	default
	Stream<String> asStream() {
		final Builder<String> builder = new Builder<>();
		addToCommand(builder);
		return builder.build().stream();
	}
}
