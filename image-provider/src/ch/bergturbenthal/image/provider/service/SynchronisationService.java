package ch.bergturbenthal.image.provider.service;

import java.io.File;

import android.database.Cursor;

public interface SynchronisationService {
  File getLoadedThumbnail(final int thumbnailId);

  Cursor readAlbumEntryList(final int albumId, final String[] projection);

  Cursor readAlbumList(final String[] projection);

  Cursor readServerList(final String[] projection);

  Cursor readServerProgresList(final String string, final String[] projection);
}
