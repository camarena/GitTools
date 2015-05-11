package org.camarena.tools.oscommands.git;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

/**
 * @author Hermán de J. Camarena R.
 */
public class CommitInfo {
	public CommitInfo(@Nonnull final String author,
	                  @Nonnull final String date,
	                  @Nonnull final String comment,
	                  @Nonnull final ImmutableList<String> parents) {
		mAuthor = author;
		mDate = date;
		mComment = comment;
		//noinspection AssignmentToCollectionOrArrayFieldFromParameter
		mParents = parents;
	}

	@Nonnull
	public String getAuthor() {
		return mAuthor;
	}

	@Nonnull
	public String getComment() {
		return mComment;
	}

	@Nonnull
	public String getDate() {
		return mDate;
	}

	@Nonnull
	public ImmutableList<String> getParents() {
		//noinspection ReturnOfCollectionOrArrayField
		return mParents;
	}

	public boolean isMerge() {
		return mParents.size() > 1;
	}

	public boolean isRoot() {
		return mParents.isEmpty();
	}

	@Nonnull
	private final String mAuthor;
	@Nonnull
	private final String mDate;
	@Nonnull
	private final String mComment;
	@Nonnull
	private final ImmutableList<String> mParents;
}
