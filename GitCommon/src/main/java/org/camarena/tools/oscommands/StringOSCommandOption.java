package org.camarena.tools.oscommands;

import com.google.common.collect.ImmutableList.Builder;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Hermán de J. Camarena R.
 */
public
class StringOSCommandOption extends SimpleOSCommandOption {
	@Nonnull
	private final OutputKind mKind;
	@Nonnull
	private final Optional<String> mValue;

	public
	StringOSCommandOption(@Nonnull final String option, @Nonnull final Optional<String> value) {
		this(option, value, OutputKind.USE_EQUAL);
	}

	public
	StringOSCommandOption(@Nonnull final String option,
	                      @Nonnull final Optional<String> value,
	                      @Nonnull final OutputKind kind) {
		super(option);
		mKind = kind;
		Objects.requireNonNull(value);
		mValue = value;
	}

	@Override
	public
	void addToCommand(@Nonnull final Builder<String> builder) {
		if (mKind == OutputKind.USE_EQUAL) {
			String val = getOption();
			if (mValue.isPresent())
				val += '=' + mValue.get();
			builder.add(val);
		}
		else {
			builder.add(getOption());
			mValue.ifPresent(builder::add);
		}
	}

	public
	enum OutputKind {
		USE_EQUAL,
		USE_TWO_ARGUMENTS
	}
}
