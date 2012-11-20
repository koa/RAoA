package ch.bergturbenthal.image.provider.service;

import java.io.File;

import android.database.Cursor;

public interface SynchronisationService {
  File getLoadedThumbnail(int thumbnailId);

  Cursor readAlbumEntryList(int albumId, String[] projection);

  Cursor readAlbumList(final String[] projection);
}
