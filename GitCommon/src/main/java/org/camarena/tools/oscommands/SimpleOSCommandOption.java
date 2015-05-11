package org.camarena.tools.oscommands;

import com.google.common.collect.ImmutableList.Builder;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author Hermán de J. Camarena R.
 */
public class SimpleOSCommandOption implements OSCommandOption {
    @Nonnull
    private final String mOption;

    public SimpleOSCommandOption(@Nonnull final String option) {
        Objects.requireNonNull(option);
        mOption = option;
    }

    @Nonnull
    public String getOption() {
        return mOption;
    }

    @Override
    public void addToCommand(@Nonnull final Builder<String> builder) {
        builder.add(mOption);
    }
}
