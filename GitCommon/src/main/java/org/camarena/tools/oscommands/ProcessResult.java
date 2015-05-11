package org.camarena.tools.oscommands;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Optional;

/**
 * @author Hermán de J. Camarena R.
 */
@SuppressWarnings({"AssignmentToCollectionOrArrayFieldFromParameter", "ReturnOfCollectionOrArrayField"})
public
class ProcessResult {
	private final int            mExitValue;
	@Nonnull
	private final Optional<File> mWorkingDirectory;
	@Nonnull
	private final String[] mArgs;
	@Nonnull
	private final String mStdOut;
	@Nonnull
	private final String mStdErr;

	@Nonnull
	public
	String[] getArgs() {
		return mArgs;
	}

	@Nonnull
	public
	Optional<File> getWorkingDirectory() {
		return mWorkingDirectory;
	}

	public
	ProcessResult(final int exitValue,
	              @Nonnull final byte[] stdOut,
	              @Nonnull final byte[] stdErr,
	              @Nonnull final Optional<File> workingDirectory, @Nonnull final String[] args) {
		mExitValue = exitValue;
		mWorkingDirectory = workingDirectory;
		mArgs = args;
		mStdOut = new String(stdOut);
		mStdErr = new String(stdErr);
	}

	public
	int getExitValue() {
		return mExitValue;
	}

	@Nonnull
	public
	String getStdErr() {
		return mStdErr;
	}

	@Nonnull
	public
	String getStdOut() {
		return mStdOut;
	}
}
