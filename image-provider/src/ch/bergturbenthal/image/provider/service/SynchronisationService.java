package ch.bergturbenthal.image.provider.service;

import java.io.File;

import android.content.ContentValues;
import android.database.Cursor;

public interface SynchronisationService {
  File getLoadedThumbnail(final int thumbnailId);

  Cursor readAlbumEntryList(final int albumId, final String[] projection);

  Cursor readAlbumList(final String[] projection);

  Cursor readServerList(final String[] projection);

  Cursor readServerProgresList(final String string, final String[] projection);

  Cursor readSingleAlbum(final int albumId, final String[] projection);

  int updateAlbumEntry(final int albumId, final ContentValues values);
}
