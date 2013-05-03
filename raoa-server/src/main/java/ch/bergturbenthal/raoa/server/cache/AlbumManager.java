package ch.bergturbenthal.raoa.server.cache;

import ch.bergturbenthal.raoa.server.metadata.PicasaIniData;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;

public interface AlbumManager {
	AlbumEntryData getCachedData();

	PicasaIniData getPicasaData();

	void updateCache(final AlbumEntryData entryData);

	void recordThumbnailException(final String image, final Throwable ex);

	void clearThumbnailException(final String image);
}
