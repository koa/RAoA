package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import android.util.Pair;
import ch.bergturbenthal.raoa.data.model.StorageList;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntries;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMutationData;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumState;
import ch.bergturbenthal.raoa.provider.store.FileBackend;
import ch.bergturbenthal.raoa.provider.store.FileStorage;
import ch.bergturbenthal.raoa.provider.store.FileStorage.ReadPolicy;
import ch.bergturbenthal.raoa.provider.store.JacksonBackend;
import ch.bergturbenthal.raoa.provider.store.ParcelableBackend;

public class LocalStore {

	private static final String METADATA_SUFFIX = "-metadata";
	private final FileStorage store;

	@SuppressWarnings("unchecked")
	public LocalStore(final File dataDir) {
		ParcelableBackend.checkVersion(dataDir, 6);
		store = new FileStorage(Arrays.asList((FileBackend<?>) new ParcelableBackend<AlbumEntries>(dataDir, AlbumEntries.class),
																					(FileBackend<?>) new ParcelableBackend<AlbumMeta>(dataDir, AlbumMeta.class),
																					(FileBackend<?>) new ParcelableBackend<AlbumMutationData>(dataDir, AlbumMutationData.class),
																					(FileBackend<?>) new JacksonBackend<AlbumState>(dataDir, AlbumState.class),
																					(FileBackend<?>) new JacksonBackend<StorageList>(dataDir, StorageList.class)

		));

	}

	public <V> V callInTransaction(final Callable<V> callable) {
		return store.callInTransaction(callable);
	}

	public AlbumEntries getAlbumEntries(final String archiveName, final String albumId, final ReadPolicy policy) {
		return store.getObject(archiveName + "/" + albumId + "-entries", AlbumEntries.class, policy);
	}

	public AlbumMeta getAlbumMeta(final String archiveName, final String albumId, final ReadPolicy policy) {
		final AlbumMeta value = store.getObject(archiveName + "/" + albumId + METADATA_SUFFIX, AlbumMeta.class, policy);
		if (policy == ReadPolicy.READ_OR_CREATE) {
			value.setArchiveName(archiveName);
			value.setAlbumId(albumId);
		}
		return value;
	}

	public AlbumMutationData getAlbumMutationData(final String archiveName, final String albumId, final ReadPolicy policy) {
		return store.getObject(makeAlbumMutationDataPath(archiveName, albumId), AlbumMutationData.class, policy);
	}

	public AlbumState getAlbumState(final String archiveName, final String albumId, final ReadPolicy policy) {
		final String relativePath = archiveName + "/" + albumId + "-state";
		return store.getObject(relativePath, AlbumState.class, policy);
	}

	public StorageList getCurrentStorageList(final ReadPolicy policy) {
		return store.getObject("storages", StorageList.class, policy);
	}

	public Collection<Pair<String, String>> listAlbumMeta() {
		final Collection<String> foundPath = store.listRelativePath(Arrays.asList(Pattern.compile(".*"), Pattern.compile(".*" + METADATA_SUFFIX)), AlbumMeta.class);
		final ArrayList<Pair<String, String>> ret = new ArrayList<Pair<String, String>>(foundPath.size());
		for (final String string : foundPath) {
			final String[] parts = string.split("/", 2);
			if (parts.length != 2) {
				continue;
			}
			final String filename = parts[1];
			if (!filename.endsWith(METADATA_SUFFIX)) {
				continue;
			}
			ret.add(new Pair<String, String>(parts[0], filename.substring(0, filename.length() - METADATA_SUFFIX.length())));
		}
		return ret;
	}

	public void removeMutationData(final String archiveName, final String albumId) {
		store.removeObject(makeAlbumMutationDataPath(archiveName, albumId), AlbumMutationData.class);
	}

	private String makeAlbumMutationDataPath(final String archiveName, final String albumId) {
		return archiveName + "/" + albumId + "-detail";
	}

}
