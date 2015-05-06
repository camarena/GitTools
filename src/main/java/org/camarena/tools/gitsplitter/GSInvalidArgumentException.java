package org.camarena.tools.gitsplitter;

/**
 * @author Hermán de J. Camarena R.
 */
public class GSInvalidArgumentException extends GSException {
	public GSInvalidArgumentException() {
	}

	public GSInvalidArgumentException(final Throwable cause) {
		super(cause);
	}

	public GSInvalidArgumentException(final String message) {
		super(message);
	}

	public GSInvalidArgumentException(final String message, final Throwable cause) {
		super(message, cause);
	}

	protected GSInvalidArgumentException(final String message,
	                                     final Throwable cause,
	                                     final boolean enableSuppression,
	                                     final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	private static final long serialVersionUID = 8970537818774773789L;
}
