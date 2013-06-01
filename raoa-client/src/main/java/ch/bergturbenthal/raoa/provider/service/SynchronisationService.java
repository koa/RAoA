package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.util.Date;

import android.content.ContentValues;
import android.database.Cursor;

public interface SynchronisationService {
	File getLoadedThumbnail(final String archiveName, final String albumName, final String albumEntryName);

	Cursor readAlbumEntryList(final String archiveName, final String albumName, final String[] projection);

	Cursor readAlbumList(final String[] projection);

	Cursor readServerList(final String[] projection);

	Cursor readServerProgresList(final String server, final String[] projection);

	Cursor readSingleAlbum(final String archiveName, final String albumName, final String[] projection);

	int updateAlbum(final String archiveName, final String albumName, final ContentValues values);

	Cursor readServerIssueList(final String server, final String[] projection);

	int updateAlbumEntry(final String archiveName, final String albumName, final String albumEntryName, final ContentValues values);

	Cursor readSingleAlbumEntry(final String archiveName, final String albumName, final String albumEntryName, final String[] projection);

	void createAlbumOnServer(final String server, final String fullAlbumName, final Date autoAddDate);

	String getContenttype(final String archive, final String albumId, final String image);

	Cursor readKeywordStatistics(final String[] projection);
}
