package org.camarena.tools;

/**
 * @author Hermán de J. Camarena R.
 */
public class CLIInvalidArgumentException extends CLIException {
	public CLIInvalidArgumentException() {
	}

	public CLIInvalidArgumentException(final Throwable cause) {
		super(cause);
	}

	public CLIInvalidArgumentException(final String message) {
		super(message);
	}

	public CLIInvalidArgumentException(final String message, final Throwable cause) {
		super(message, cause);
	}

	protected CLIInvalidArgumentException(final String message,
	                                      final Throwable cause,
	                                      final boolean enableSuppression,
	                                      final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	private static final long serialVersionUID = 8970537818774773789L;
}
