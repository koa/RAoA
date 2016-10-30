package ch.bergturbenthal.raoa.server.spring.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class CachingSupplier<V> implements Supplier<V> {
	private final AtomicReference<Optional<V>> cachedValue = new AtomicReference<Optional<V>>(null);
	private final AtomicReference<Exception> exceptionOnTake = new AtomicReference<Exception>(null);
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Supplier<V> supplier;

	public CachingSupplier(final Supplier<V> supplier) {
		this.supplier = supplier;
	}

	@Override
	public V get() {
		while (true) {
			final Lock readLock = lock.readLock();
			readLock.lock();
			try {
				final Optional<V> alreadyCachedValue = cachedValue.get();
				if (alreadyCachedValue != null) {
					return alreadyCachedValue.orElse(null);
				}
				final Exception takenException = exceptionOnTake.get();
				if (takenException != null) {
					throw new RuntimeException(takenException);
				}

			} finally {
				readLock.unlock();
			}
			final Lock writeLock = lock.writeLock();
			writeLock.lock();
			try {
				if (cachedValue.get() == null && exceptionOnTake.get() == null) {
					try {
						cachedValue.set(Optional.ofNullable(supplier.get()));
					} catch (final Exception ex) {
						exceptionOnTake.set(ex);
					}
				}
			} finally {
				writeLock.unlock();
			}
		}
	}

}
