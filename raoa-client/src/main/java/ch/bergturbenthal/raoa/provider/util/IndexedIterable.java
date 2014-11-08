package ch.bergturbenthal.raoa.provider.util;

public interface IndexedIterable<T> extends Iterable<T> {
	T get(final int index);

	int size();
}
