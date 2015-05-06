package org.camarena.tools.gitsplitter;

/**
 * @author Hermán de J. Camarena R.
 */
public class GSException extends Exception {
	public GSException() {
	}

	public GSException(final Throwable cause) {
		super(cause);
	}

	public GSException(final String message) {
		super(message);
	}

	public GSException(final String message, final Throwable cause) {
		super(message, cause);
	}

	protected GSException(final String message,
	                      final Throwable cause,
	                      final boolean enableSuppression,
	                      final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
