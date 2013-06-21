package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntries;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumIndex;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMutationData;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumState;
import ch.bergturbenthal.raoa.provider.store.ParcelableBackend;
import ch.bergturbenthal.raoa.util.store.FileBackend;
import ch.bergturbenthal.raoa.util.store.FileStorage;
import ch.bergturbenthal.raoa.util.store.JacksonBackend;
import ch.bergturbenthal.raoa.util.store.FileStorage.ReadPolicy;

public class LocalStore {

	private static final String METADATA_SUFFIX = "-metadata";
	private final FileStorage store;

	@SuppressWarnings("unchecked")
	public LocalStore(final File dataDir) {
		ParcelableBackend.checkVersion(dataDir, 6);
		store = new FileStorage(Arrays.asList((FileBackend<?>) new ParcelableBackend<AlbumEntries>(dataDir, AlbumEntries.class),
																					(FileBackend<?>) new ParcelableBackend<AlbumMeta>(dataDir, AlbumMeta.class),
																					(FileBackend<?>) new JacksonBackend<AlbumMutationData>(dataDir, AlbumMutationData.class),
																					(FileBackend<?>) new JacksonBackend<AlbumState>(dataDir, AlbumState.class),
																					(FileBackend<?>) new JacksonBackend<ArchiveMeta>(dataDir, ArchiveMeta.class)

		));

	}

	public <V> V callInTransaction(final Callable<V> callable) {
		return store.callInTransaction(callable);
	}

	public AlbumEntries getAlbumEntries(final AlbumIndex entry, final ReadPolicy policy) {
		return store.getObject(entry.getArchiveName() + "/" + entry.getAlbumId() + "-entries", AlbumEntries.class, policy);
	}

	public AlbumMeta getAlbumMeta(final AlbumIndex entry, final ReadPolicy policy) {
		final AlbumMeta value = store.getObject(entry.getArchiveName() + "/" + entry.getAlbumId() + METADATA_SUFFIX, AlbumMeta.class, policy);
		if (policy == ReadPolicy.READ_OR_CREATE) {
			value.setArchiveName(entry.getArchiveName());
			value.setAlbumId(entry.getAlbumId());
		}
		return value;

	}

	public AlbumMutationData getAlbumMutationData(final AlbumIndex index, final ReadPolicy policy) {
		return store.getObject(makeAlbumMutationDataPath(index.getArchiveName(), index.getAlbumId()), AlbumMutationData.class, policy);
	}

	public AlbumState getAlbumState(final AlbumIndex index, final ReadPolicy policy) {
		final String relativePath = index.getArchiveName() + "/" + index.getAlbumId() + "-state";
		return store.getObject(relativePath, AlbumState.class, policy);
	}

	public ArchiveMeta getArchiveMeta(final String archive, final ReadPolicy policy) {
		return store.getObject("storages/" + archive, ArchiveMeta.class, policy);
	}

	public Collection<AlbumIndex> listAlbumMeta() {
		final Collection<String> foundPath = store.listRelativePath(Arrays.asList(Pattern.compile(".*"), Pattern.compile(".*" + METADATA_SUFFIX)), AlbumMeta.class);
		final ArrayList<AlbumIndex> ret = new ArrayList<AlbumIndex>(foundPath.size());
		for (final String string : foundPath) {
			final String[] parts = string.split("/", 2);
			if (parts.length != 2) {
				continue;
			}
			final String filename = parts[1];
			if (!filename.endsWith(METADATA_SUFFIX)) {
				continue;
			}
			ret.add(new AlbumIndex(parts[0], filename.substring(0, filename.length() - METADATA_SUFFIX.length())));
		}
		return ret;
	}

	public void removeMutationData(final AlbumIndex index) {
		store.removeObject(makeAlbumMutationDataPath(index.getArchiveName(), index.getAlbumId()), AlbumMutationData.class);
	}

	private String makeAlbumMutationDataPath(final String archiveName, final String albumId) {
		return archiveName + "/" + albumId + "-detail";
	}

}
