package ch.bergturbenthal.raoa.server.spring.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.json.AlbumMetadata;
import ch.bergturbenthal.raoa.json.InstanceData;

public interface AlbumAccess {
	boolean addAutoaddBeginDate(String album, Instant instant);

	String createAlbum(String[] pathComps);

	AlbumMetadata getAlbumMetadata(String albumId);

	Collection<Instant> getAutoaddBeginDates(final String album);

	InstanceData getInstanceData();

	List<String> listAlbums();

	AlbumEntry takeAlbumEntry(String album);
}
