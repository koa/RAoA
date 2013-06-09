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

	public static <K, V> Lookup<K, V> loadLazy(final Lookup<K, V> loader) {
		return new Lookup<K, V>() {
			private final Map<K, V> loadedValues = new HashMap<K, V>();

			@Override
			public synchronized V get(final K key) {
				if (loadedValues.containsKey(key)) {
					return loader.get(key);
				}
				final V loadedValue = loader.get(key);
				loadedValues.put(key, loadedValue);
				return loadedValue;
			}
		};
	}
}
