package ch.bergturbenthal.image.server.cache;

import ch.bergturbenthal.image.server.metadata.PicasaIniData;
import ch.bergturbenthal.image.server.model.AlbumEntryData;

public interface AlbumManager {
  AlbumEntryData getCachedData();

  PicasaIniData getPicasaData();

  void updateCache(final AlbumEntryData entryData);

  void recordThumbnailException(final String image, final Throwable ex);

  void clearThumbnailException(final String image);
}
