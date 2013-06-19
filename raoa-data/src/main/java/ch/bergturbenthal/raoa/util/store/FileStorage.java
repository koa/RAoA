package ch.bergturbenthal.raoa.util.store;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import ch.bergturbenthal.raoa.util.Pair;
import ch.bergturbenthal.raoa.util.store.FileBackend.CommitExecutor;

public class FileStorage {
	public static enum ReadPolicy {
		READ_IF_EXISTS, READ_ONLY, READ_OR_CREATE
	}

	private class Transaction {
		private final Map<Pair<Class<Object>, String>, Date> lastModified = new HashMap<Pair<Class<Object>, String>, Date>();
		private final Map<Pair<Class<Object>, String>, Object> referencedObjects = new HashMap<Pair<Class<Object>, String>, Object>();
		private boolean rollbackOnly = false;

		synchronized void commit() {
			final long start = System.currentTimeMillis();
			try {
				if (rollbackOnly) {
					return;
				}
				final Collection<FileBackend.CommitExecutor> executors = new ArrayList<FileBackend.CommitExecutor>();
				for (final Entry<Pair<Class<Object>, String>, Object> referencedEntry : referencedObjects.entrySet()) {
					final FileBackend<Object> backend = getBackend(referencedEntry.getKey().first);
					final String relativePath = referencedEntry.getKey().second;
					if (!nullSafeEquals(backend.getLastModified(relativePath), lastModified.get(referencedEntry.getKey()))) {
						throw new ConcurrentTransactionException(relativePath);
					}
					executors.add(backend.save(relativePath, referencedEntry.getValue()));
				}
				// prepare all
				boolean prepareOk = true;
				boolean prepareFinished = false;
				try {
					for (final CommitExecutor commitExecutor : executors) {
						prepareOk &= commitExecutor.prepare();
					}
					prepareFinished = true;
				} finally {
					if (prepareOk && prepareFinished) {
						for (final CommitExecutor commitExecutor : executors) {
							commitExecutor.commit();
						}
						// update read-only cache
						for (final Entry<Pair<Class<Object>, String>, Object> entry : referencedObjects.entrySet()) {
							final Object value = entry.getValue();
							final Pair<Class<Object>, String> key = entry.getKey();
							if (value == null) {
								readOnlyCache.remove(key);
							} else {
								readOnlyCache.put(key, new WeakReference<Object>(value));
							}
						}
					} else {
						for (final CommitExecutor commitExecutor : executors) {
							commitExecutor.abort();
						}
					}
				}
			} finally {
				final long time = System.currentTimeMillis() - start;
				if (time > 50) {
					// Log.i("performance commit", "commit of " + referencedObjects.size() + " took " + time + " ms");
				}
				referencedObjects.clear();
				lastModified.clear();
			}
		}

		@SuppressWarnings("unchecked")
		synchronized <D> D getObject(final String relativePath, final Class<D> type) {
			final Pair<Class<Object>, String> key = new Pair<Class<Object>, String>((Class<Object>) type, relativePath);
			if (referencedObjects.containsKey(key)) {
				return (D) referencedObjects.get(key);
			}
			final FileBackend<D> backend = getBackend(type);
			lastModified.put(key, backend.getLastModified(relativePath));
			final D loadedValue = backend.load(relativePath);
			referencedObjects.put(key, loadedValue);
			return loadedValue;
		}

		synchronized <D> Collection<String> listRelativePath(final List<Pattern> pathPatterns, final Class<D> type) {
			final Collection<String> ret = new LinkedHashSet<String>();
			cacheLoop:
			for (final Entry<Pair<Class<Object>, String>, Object> referencedEntry : referencedObjects.entrySet()) {
				final Pair<Class<Object>, String> key = referencedEntry.getKey();
				if (!key.first.equals(type)) {
					continue;
				}
				final String cacheEntryPath = key.second;
				final String[] pathComps = cacheEntryPath.split("/");
				if (pathComps.length != pathPatterns.size()) {
					continue;
				}
				for (int i = 0; i < pathComps.length; i++) {
					if (!pathPatterns.get(i).matcher(pathComps[i]).matches()) {
						continue cacheLoop;
					}
				}
				if (referencedEntry.getValue() == null) {
					ret.remove(cacheEntryPath);
				} else {
					ret.add(cacheEntryPath);
				}
			}
			return ret;
		}

		synchronized <D> void putObject(final String relativePath, final D value) {
			final Pair<Class<Object>, String> key = new Pair<Class<Object>, String>((Class<Object>) value.getClass(), relativePath);
			referencedObjects.put(key, value);
		}

		synchronized <D> void remove(final String relativePath, final Class<D> type) {
			final Pair<Class<Object>, String> key = new Pair<Class<Object>, String>((Class<Object>) type, relativePath);
			referencedObjects.put(key, null);
		}

		void setRollbackOnly() {
			rollbackOnly = true;
		}
	}

	/**
	 * @param <T>
	 * @param lastModified
	 * @param date
	 * @return
	 */
	private static <T> boolean nullSafeEquals(final T v1, final T v2) {
		if (v1 == v2) {
			return true;
		}
		if (v1 == null || v2 == null) {
			return false;
		}
		return v1.equals(v2);
	}

	private final ThreadLocal<Transaction> currentTransaction = new ThreadLocal<Transaction>();

	private final Map<Pair<Class<Object>, String>, WeakReference<Object>> readOnlyCache = new ConcurrentHashMap<Pair<Class<Object>, String>, WeakReference<Object>>();

	private final Map<Class<?>, FileBackend<?>> registeredBackends = new HashMap<Class<?>, FileBackend<?>>();

	public FileStorage(final Collection<FileBackend<?>> backends) {
		for (final FileBackend<?> fileBackend : backends) {
			registeredBackends.put(fileBackend.getType(), fileBackend);
		}
	}

	public <V> V callInTransaction(final Callable<V> callable) {
		final Transaction oldTransaction = currentTransaction.get();
		if (oldTransaction != null) {
			throw new RuntimeException("Dont nest Transactions");
		}
		currentTransaction.set(new Transaction());
		try {
			while (true) {
				try {
					return callable.call();
				} catch (final Throwable t) {
					currentTransaction.get().setRollbackOnly();
					throw new RuntimeException("Error in Transaction", t);
				} finally {
					synchronized (this) {
						currentTransaction.get().commit();
					}
				}
			}
		} finally {
			currentTransaction.set(null);
		}
	}

	public <D> D getObject(final String relativePath, final Class<D> type, final ReadPolicy policy) {
		if (policy == ReadPolicy.READ_ONLY) {
			return getObjectReadOnly(relativePath, type);
		}
		final Transaction transaction = currentTransaction.get();
		final D currentObject = transaction.getObject(relativePath, type);
		if (currentObject != null || policy == ReadPolicy.READ_IF_EXISTS) {
			return currentObject;
		}
		D newInstance;
		try {
			newInstance = type.newInstance();
		} catch (final Throwable e) {
			throw new RuntimeException("Cannot instanciate " + type + " for storing at " + relativePath);
		}
		transaction.putObject(relativePath, newInstance);
		return newInstance;
	}

	public <D> Collection<String> listRelativePath(final List<Pattern> pathPatterns, final Class<D> type) {
		final Collection<String> storedPaths = new LinkedHashSet<String>(getBackend(type).listRelativePath(pathPatterns));
		final Transaction transaction = currentTransaction.get();
		if (transaction != null) {
			storedPaths.addAll(transaction.listRelativePath(pathPatterns, type));
		}
		return storedPaths;
	}

	public <D> void putObject(final String relativePath, final D value) {
		currentTransaction.get().putObject(relativePath, value);
	}

	public <D> void removeObject(final String relativePath, final Class<D> type) {
		currentTransaction.get().remove(relativePath, type);
	}

	@SuppressWarnings("unchecked")
	private <T> FileBackend<T> getBackend(final Class<T> type) {
		return (FileBackend<T>) registeredBackends.get(type);
	}

	@SuppressWarnings("unchecked")
	private <D> D getObjectReadOnly(final String relativePath, final Class<D> type) {
		final Pair<Class<Object>, String> key = new Pair<Class<Object>, String>((Class<Object>) type, relativePath);
		final WeakReference<D> existingEntry = (WeakReference<D>) readOnlyCache.get(key);
		if (existingEntry != null && existingEntry.get() != null) {
			return existingEntry.get();
		}
		synchronized (readOnlyCache) {
			final WeakReference<D> betweenLoadedEntry = (WeakReference<D>) readOnlyCache.get(key);
			if (betweenLoadedEntry != null && betweenLoadedEntry.get() != null) {
				return betweenLoadedEntry.get();
			}
			final D loaded = getBackend(type).load(relativePath);
			readOnlyCache.put(key, new WeakReference<Object>(loaded));
			return loaded;
		}
	}
}
