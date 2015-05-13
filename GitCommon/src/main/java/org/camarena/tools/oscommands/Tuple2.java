package org.camarena.tools.oscommands;

import javax.annotation.Nonnull;

/**
 * @author Hermán de J. Camarena R.
 */
public
final
class Tuple2<A, B> {
	@Nonnull
	private final A mA;
	@Nonnull
	private final B mB;

	public
	Tuple2(@Nonnull final A a, @Nonnull final B b) {

		mA = a;
		mB = b;
	}

	@Nonnull
	public
	A get_1() {
		return mA;
	}

	@Nonnull
	public
	B get_2() {
		return mB;
	}
}
