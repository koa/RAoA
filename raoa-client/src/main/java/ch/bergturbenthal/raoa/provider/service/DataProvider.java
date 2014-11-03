package ch.bergturbenthal.raoa.provider.service;

import java.io.Closeable;
import java.util.Date;
import java.util.Map;
import java.util.NavigableSet;

import lombok.Getter;

import org.mapdb10.BTreeMap;
import org.mapdb10.Bind;
import org.mapdb10.DB;
import org.mapdb10.Fun;
import org.mapdb10.Fun.Function2;
import org.mapdb10.Fun.Tuple2;

import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryIndex;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumIndex;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMutationData;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumState;

@Getter
class DataProvider implements Closeable {
	private final Map<AlbumEntryIndex, Date> albumEntryCreationDate;
	private final Map<AlbumEntryIndex, String> albumEntryFileName;
	private final BTreeMap<AlbumEntryIndex, AlbumEntryDto> albumEntryMap;
	private final BTreeMap<AlbumIndex, AlbumMeta> albumMetadataMap;
	private final BTreeMap<AlbumIndex, AlbumMutationData> albumMutationDataMap;
	private final BTreeMap<AlbumIndex, AlbumState> albumStateMap;
	private final BTreeMap<String, ArchiveMeta> archiveMetaMap;
	private final DB db;
	private final NavigableSet<Tuple2<AlbumIndex, AlbumEntryIndex>> entriesByAlbumSet;

	public DataProvider(final DB db) {
		this.db = db;
		albumMetadataMap = db.<AlbumIndex, AlbumMeta> getTreeMap("album_meta_map");
		albumMutationDataMap = db.<AlbumIndex, AlbumMutationData> getTreeMap("album_mutation_data");
		albumEntryMap = db.<AlbumEntryIndex, AlbumEntryDto> getTreeMap("album_entry_data");
		entriesByAlbumSet = db.<Fun.Tuple2<AlbumIndex, AlbumEntryIndex>> getTreeSet("album_entry_by_album");
		archiveMetaMap = db.<String, ArchiveMeta> getTreeMap("archive_meta");
		albumStateMap = db.getTreeMap("album_state");
		albumEntryCreationDate = db.getTreeMap("album_entry_creation_date");
		albumEntryFileName = db.getTreeMap("album_entry_file_name");
		Bind.secondaryKey(albumEntryMap, entriesByAlbumSet, new Function2<AlbumIndex, AlbumEntryIndex, AlbumEntryDto>() {
			@Override
			public AlbumIndex run(final AlbumEntryIndex a, final AlbumEntryDto b) {
				return a.getAlbumIndex();
			}
		});
		Bind.secondaryValue(albumEntryMap, albumEntryCreationDate, new Function2<Date, AlbumEntryIndex, AlbumEntryDto>() {
			@Override
			public Date run(final AlbumEntryIndex a, final AlbumEntryDto b) {
				final Date captureDate = b.getCaptureDate();
				if (captureDate == null) {
					return new Date(0);
				}
				return captureDate;
			}
		});
		Bind.secondaryValue(albumEntryMap, albumEntryFileName, new Function2<String, AlbumEntryIndex, AlbumEntryDto>() {
			@Override
			public String run(final AlbumEntryIndex a, final AlbumEntryDto b) {
				return b.getFileName();
			}
		});
	}

	@Override
	public void close() {
		db.close();
	}

	public Iterable<AlbumEntryIndex> listEntriesByAlbum(final AlbumIndex album) {
		return Fun.filter(entriesByAlbumSet, album);
	}
}