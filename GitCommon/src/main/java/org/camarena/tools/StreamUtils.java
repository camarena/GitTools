package org.camarena.tools;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * @author Hermán de J. Camarena R.
 */
public final
class StreamUtils {
	private
	StreamUtils() {
	}

	public static
	Stream<String> linesInString(@Nonnull final String s) {
		Objects.requireNonNull(s);
		//noinspection IOResourceOpenedButNotSafelyClosed
		return new BufferedReader(new StringReader(s)).lines();
	}

	public static
	<T>
	Collector<T, Builder<T>, ImmutableList<T>> immutableListCollector() {
		return Collector.of(Builder::new,
		                    (BiConsumer<Builder<T>, T>) Builder::add,
		                    (s1, s2) -> s1.addAll(s2.build()),
		                    Builder::build);
	}

	public static
	<T>
	Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> immutableSetCollector() {
		return Collector.of(ImmutableSet.Builder::new,
		                    (BiConsumer<ImmutableSet.Builder<T>, T>) ImmutableSet.Builder::add,
		                    (s1, s2) -> s1.addAll(s2.build()),
		                    ImmutableSet.Builder::build);
	}
}
