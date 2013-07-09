/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import android.util.Log;
import android.util.LruCache;

/**
 * TODO: add type comment.
 * 
 */
public class LazyLoader {
	public static interface Callable<V> {
		V call();
	}

	public static interface Lookup<K, V> {
		V get(final K key);
	}

	private static class HashMapLookup<K, V> implements Lookup<K, V>, Closeable {
		private final Map<K, V> loadedValues = new HashMap<K, V>();
		private final Lookup<K, V> loader;
		private final AtomicInteger missCount = new AtomicInteger();
		private final AtomicInteger readCount = new AtomicInteger();

		private HashMapLookup(final Lookup<K, V> loader) {
			this.loader = loader;
		}

		@Override
		public void close() {
			Log.i("LazyLoader", "ReadCount: " + readCount + ", missCount:" + missCount);

		}

		@Override
		public synchronized V get(final K key) {
			readCount.incrementAndGet();
			if (loadedValues.containsKey(key)) {
				return loadedValues.get(key);
			}
			missCount.incrementAndGet();
			final V loadedValue = loader.get(key);
			loadedValues.put(key, loadedValue);
			return loadedValue;
		}
	}

	private static class LruLookup<K, V> implements Lookup<K, V>, Closeable {
		private final LruCache<K, V> cache;
		private final AtomicInteger missCount = new AtomicInteger();
		private final AtomicInteger readCount = new AtomicInteger();

		private LruLookup(final Lookup<K, V> loader, final int cacheCount) {
			cache = new LruCache<K, V>(cacheCount) {

				@Override
				protected V create(final K key) {
					missCount.incrementAndGet();
					return loader.get(key);
				}
			};

		}

		@Override
		public void close() throws IOException {
			Log.i("LazyLoader", "ReadCount: " + readCount + ", missCount:" + missCount);
		}

		@Override
		public V get(final K key) {
			readCount.incrementAndGet();
			return cache.get(key);
		}

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
		}
	}

	public static <K, V> Lookup<K, V> cacheLatest(final Lookup<K, V> loader, final int cacheCount) {
		return new LruLookup<K, V>(loader, cacheCount);
	}

	public static <V> Callable<V> loadLazy(final Callable<V> loader) {
		return new Callable<V>() {

			private boolean alreadyLoaded = false;
			private V result;

			@Override
			public V call() {
				if (!alreadyLoaded) {
					result = loader.call();
					alreadyLoaded = true;
				}
				return result;
			}
		};
	}

	public static <V> java.util.concurrent.Callable<V> loadLazy(final java.util.concurrent.Callable<V> loader) {
		return new java.util.concurrent.Callable<V>() {

			private boolean alreadyLoaded = false;
			private V result;

			@Override
			public V call() throws Exception {
				if (!alreadyLoaded) {
					result = loader.call();
					alreadyLoaded = true;
				}
				return result;
			}
		};
	}

	public static <K, V> Lookup<K, V> loadLazy(final Lookup<K, V> loader) {
		return new HashMapLookup<K, V>(loader);
	}
}
