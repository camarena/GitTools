package org.camarena.tools;

/**
 * @author Hermán de J. Camarena R.
 */
public class CLIException extends Exception {
	public CLIException() {
	}

	public CLIException(final Throwable cause) {
		super(cause);
	}

	public CLIException(final String message) {
		super(message);
	}

	public CLIException(final String message, final Throwable cause) {
		super(message, cause);
	}

	protected CLIException(final String message,
	                       final Throwable cause,
	                       final boolean enableSuppression,
	                       final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
