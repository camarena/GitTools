package org.camarena.tools.oscommands.git;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.camarena.tools.StreamUtils;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Hermán de J. Camarena R.
 */
public
class GitArgument implements GitLogOption, GitCheckoutOption, GitBranchOption, GitMergeOption, GitFilterBranchOption,
                             GitRemoteOption {

	@Nonnull
	private final ImmutableList<String> mArguments;

	public
	GitArgument(@Nonnull final String first, @Nonnull final String... rest) {
		this(first, Arrays.stream(rest));
	}

	public
	GitArgument(@Nonnull final String first, @Nonnull final Stream<String> rest) {
		Objects.requireNonNull(first);
		mArguments = Stream.concat(Stream.of(first), rest).collect(StreamUtils.immutableListCollector());
	}

	public
	GitArgument(@Nonnull final Stream<String> args) {
		mArguments = args.collect(StreamUtils.immutableListCollector());
	}

	public static
	GitArgument arguments(@Nonnull final String first, @Nonnull final String... rest) {
		return new GitArgument(first, rest);
	}

	public static
	GitArgument arguments(@Nonnull final String first, @Nonnull final Stream<String> rest) {
		return new GitArgument(first, rest);
	}

	public static
	GitArgument arguments(@Nonnull final Stream<String> rest) {
		return new GitArgument(rest);
	}


	@Override
	public
	void addToCommand(@Nonnull final Builder<String> builder) {
		mArguments.forEach(builder::add);
	}
}
