package org.camarena.tools.oscommands;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

/**
 * @author Hermán de J. Camarena R.
 */
public class IntOSCommandOption extends SimpleOSCommandOption {
    private final int mValue;

    public IntOSCommandOption(@Nonnull final String option, final int value) {
        super(option);
        mValue = value;
    }

    @Override
    public void addToCommand(@Nonnull final ImmutableList.Builder<String> builder) {
        builder.add(getOption() + '=' + mValue);
    }
}
