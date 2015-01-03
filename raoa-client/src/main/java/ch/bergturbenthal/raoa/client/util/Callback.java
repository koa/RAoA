package ch.bergturbenthal.raoa.client.util;

public interface Callback<V> {
  void complete(final V value);

  void exception(final Exception ex);
}
