package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.util.Date;

import android.content.ContentValues;
import android.database.Cursor;
import ch.bergturbenthal.raoa.provider.SortOrder;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;

public interface SynchronisationService {
	File getLoadedThumbnail(final String archiveName, final String albumName, final String albumEntryName);

	Cursor readAlbumEntryList(final String archiveName, final String albumName, final String[] projection, final Criterium criterium, final SortOrder order);

	Cursor readAlbumList(final String[] projection, final Criterium criterium, final SortOrder order);

	Cursor readServerList(final String[] projection, final Criterium criterium, final SortOrder order);

	Cursor readServerProgresList(final String server, final String[] projection, final Criterium criterium, final SortOrder order);

	Cursor readSingleAlbum(final String archiveName, final String albumName, final String[] projection, final Criterium criterium, final SortOrder order);

	int updateAlbum(final String archiveName, final String albumName, final ContentValues values);

	Cursor readServerIssueList(final String server, final String[] projection, final Criterium criterium, final SortOrder order);

	int updateAlbumEntry(final String archiveName, final String albumName, final String albumEntryName, final ContentValues values);

	Cursor readSingleAlbumEntry(final String archiveName,
															final String albumName,
															final String albumEntryName,
															final String[] projection,
															final Criterium criterium,
															final SortOrder order);

	void createAlbumOnServer(final String server, final String fullAlbumName, final Date autoAddDate);

	String getContenttype(final String archive, final String albumId, final String image);

	Cursor readKeywordStatistics(final String[] projection, final Criterium criterium, final SortOrder order);

	Cursor readStorages(final String[] projection, final Criterium criterium, final SortOrder order);

	void importFile(String serverName, final String filename, final byte[] data);
}
