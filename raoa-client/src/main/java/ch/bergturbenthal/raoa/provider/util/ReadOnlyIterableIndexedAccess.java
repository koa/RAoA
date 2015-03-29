package ch.bergturbenthal.raoa.provider.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReadOnlyIterableIndexedAccess<E> implements Iterable<E>, IndexedIterable<E> {
	private final int	        itemCount;

	private final Iterator<E>	iterator;
	private final List<E>	    readValues	= new ArrayList<E>();

	public ReadOnlyIterableIndexedAccess(final Iterator<E> iterator, final int itemCount) {
		super();
		this.iterator = iterator;
		this.itemCount = itemCount;
	}

	@Override
	public synchronized E get(final int index) {
		if (index < 0 || index >= itemCount)
			throw new IndexOutOfBoundsException("Index " + index + " is greater than " + itemCount);
		while (readValues.size() <= index) {
			readValues.add(iterator.next());
		}
		return readValues.get(index);
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			int	currentIndex	= 0;

			@Override
			public boolean hasNext() {
				return currentIndex < itemCount;
			}

			@Override
			public E next() {
				if (!hasNext())
					// itemCount = currentIndex;
					return null;
				return get(currentIndex++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int size() {
		return itemCount;
	}

}
