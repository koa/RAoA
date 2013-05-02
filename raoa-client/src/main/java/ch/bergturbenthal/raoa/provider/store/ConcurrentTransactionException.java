package ch.bergturbenthal.raoa.provider.store;

public class ConcurrentTransactionException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6577181874240475098L;
	private final String file;

	public ConcurrentTransactionException(final String file) {
		super("Transaction-Conflict on file " + file);
		this.file = file;
	}
}
