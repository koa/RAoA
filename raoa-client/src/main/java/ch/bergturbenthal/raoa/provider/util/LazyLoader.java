/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.util;

import java.util.HashMap;
import java.util.Map;

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

	public static <K, V> Lookup<K, V> cacheLatest(final Lookup<K, V> loader, final int cacheCount) {
		final LruCache<K, V> cache = new LruCache<K, V>(cacheCount) {

			@Override
			protected V create(final K key) {
				return loader.get(key);
			}
		};
		return new Lookup<K, V>() {

			@Override
			public V get(final K key) {
				return cache.get(key);
			}
		};
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
		return new Lookup<K, V>() {
			private final Map<K, V> loadedValues = new HashMap<K, V>();

			@Override
			public synchronized V get(final K key) {
				if (loadedValues.containsKey(key)) {
					return loadedValues.get(key);
				}
				final V loadedValue = loader.get(key);
				loadedValues.put(key, loadedValue);
				return loadedValue;
			}
		};
	}
}
