package ch.bergturbenthal.image.provider.store;

public class ConcurrentTransactionException extends RuntimeException {
  private final String file;

  public ConcurrentTransactionException(final String file) {
    super("Transaction-Conflict on file " + file);
    this.file = file;
  }
}
