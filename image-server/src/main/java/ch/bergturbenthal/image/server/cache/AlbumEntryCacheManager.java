package ch.bergturbenthal.image.server.cache;

import ch.bergturbenthal.image.server.model.AlbumEntryData;

public interface AlbumEntryCacheManager {
  AlbumEntryData getCachedData();

  void updateCache(final AlbumEntryData entryData);
}
