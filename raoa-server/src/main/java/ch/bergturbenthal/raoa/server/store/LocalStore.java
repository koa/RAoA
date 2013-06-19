/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.server.store;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import ch.bergturbenthal.raoa.server.model.ArchiveData;
import ch.bergturbenthal.raoa.util.store.FileBackend;
import ch.bergturbenthal.raoa.util.store.FileStorage;
import ch.bergturbenthal.raoa.util.store.FileStorage.ReadPolicy;
import ch.bergturbenthal.raoa.util.store.JacksonBackend;

/**
 * TODO: add type comment.
 * 
 */
public class LocalStore {
	private final FileStorage store;

	public LocalStore(final File dataDir) {
		final Collection<?> backends = Arrays.asList(((FileBackend<?>) new JacksonBackend<ArchiveData>(dataDir, ArchiveData.class)));
		store = new FileStorage((Collection<FileBackend<?>>) backends);
	}

	public <V> V callInTransaction(final Callable<V> callable) {
		return store.callInTransaction(callable);
	}

	public ArchiveData getArchiveData(final ReadPolicy policy) {
		return store.getObject("config", ArchiveData.class, policy);
	}

}
