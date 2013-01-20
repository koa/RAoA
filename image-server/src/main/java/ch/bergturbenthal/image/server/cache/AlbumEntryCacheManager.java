package ch.bergturbenthal.image.server.cache;

import ch.bergturbenthal.image.server.metadata.PicasaIniData;
import ch.bergturbenthal.image.server.model.AlbumEntryData;

public interface AlbumEntryCacheManager {
  AlbumEntryData getCachedData();

  PicasaIniData getPicasaData();

  void updateCache(final AlbumEntryData entryData);
}
