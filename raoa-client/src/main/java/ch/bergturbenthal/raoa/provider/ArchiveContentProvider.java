package ch.bergturbenthal.raoa.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;
import ch.bergturbenthal.raoa.provider.map.NotifyableMatrixCursor;
import ch.bergturbenthal.raoa.provider.service.SynchronisationService;
import ch.bergturbenthal.raoa.provider.service.SynchronisationServiceImpl;
import ch.bergturbenthal.raoa.provider.service.SynchronisationServiceImpl.LocalBinder;
import ch.bergturbenthal.raoa.provider.util.EnumUriMatcher;
import ch.bergturbenthal.raoa.provider.util.Path;
import ch.bergturbenthal.raoa.provider.util.ThumbnailUriParser;
import ch.bergturbenthal.raoa.provider.util.ThumbnailUriParser.ThumbnailUriReceiver;

public class ArchiveContentProvider extends ContentProvider {
	public static enum UriType {
		@Path("albums/*/*")
		ALBUM, @Path("albums/*/*/entries/*")
		ALBUM_ENTRY, @Path("albums/*/*/entries")
		ALBUM_ENTRY_LIST, @Path("albums/*/*/entries/*/thumbnail")
		ALBUM_ENTRY_THUMBNAIL, @Path("albums/*/*/entries/*/thumbnail/*")
		ALBUM_ENTRY_THUMBNAIL_ALIAS, @Path("albums")
		ALBUM_LIST, @Path("keywords")
		KEYWORD, @Path("servers/*/issues")
		SERVER_ISSUE_LIST, @Path("servers")
		SERVER_LIST, @Path("servers/*/progress")
		SERVER_PROGRESS_LIST, @Path("storages")
		STORAGE_LIST

	}

	static final String TAG = "Content Provider";

	private static final Map<Class, NotifyableMatrixCursor> emptyCursors = new ConcurrentHashMap<Class, NotifyableMatrixCursor>();
	private static final EnumUriMatcher<UriType> matcher = new EnumUriMatcher<UriType>(Client.AUTHORITY, UriType.class);

	private SynchronisationService service = null;
	/** Defines callbacks for service binding, passed to bindService() */
	private final ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(final ComponentName className, final IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService
			// instance
			final LocalBinder binder = (LocalBinder) service;
			setService(binder.getService());
		}

		@Override
		public void onServiceDisconnected(final ComponentName arg0) {
			setService(null);
		}
	};

	@Override
	public Bundle call(final String method, final String arg, final Bundle extras) {
		if (Client.METHOD_CREATE_ALBUM_ON_SERVER.equals(method)) {
			final String server = arg;
			final String fullAlbumName = extras.getString(Client.PARAMETER_FULL_ALBUM_NAME);
			final Date autoAddDate = extras.containsKey(Client.PARAMETER_AUTOADD_DATE) ? new Date(extras.getLong(Client.PARAMETER_AUTOADD_DATE)) : null;
			getService().createAlbumOnServer(server, fullAlbumName, autoAddDate);
		}
		return null;
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		throw new UnsupportedOperationException("delete not supported");
	}

	@Override
	public String getType(final Uri uri) {
		// Log.i(TAG, "getType called");
		switch (matcher.match(uri)) {
		case ALBUM_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/album";
		case ALBUM:
			return "vnd.android.cursor.item/vnd." + Client.AUTHORITY + "/album";
		case ALBUM_ENTRY_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/album/entry";
		case ALBUM_ENTRY:
			return "vnd.android.cursor.item/vnd." + Client.AUTHORITY + "/album/entry";
		case ALBUM_ENTRY_THUMBNAIL:
		case ALBUM_ENTRY_THUMBNAIL_ALIAS: {
			final List<String> segments = uri.getPathSegments();
			final String archive = segments.get(1);
			final String albumId = segments.get(2);
			final String image = segments.get(4);
			return getService().getContenttype(archive, albumId, image);
		}
		case SERVER_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server";
		case SERVER_PROGRESS_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server/progress";
		case SERVER_ISSUE_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server/issues";
		case KEYWORD:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/keyword";
		case STORAGE_LIST:
			return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/storage";
		}
		throw new SQLException("Unknown Uri: " + uri);
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		switch (matcher.match(uri)) {
		default:
			throw new UnsupportedOperationException("insert not supported");
		}
	}

	@Override
	public boolean onCreate() {

		getContext().bindService(new Intent(getContext(), SynchronisationServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);

		Log.i(TAG, "Content-Provider created");
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(final Uri uri, final String mode) throws FileNotFoundException {
		// Log.i(TAG, "Open called for " + uri);
		final UriType match = matcher.match(uri);
		if (match == null) {
			return super.openFile(uri, mode);
		}
		switch (match) {
		case ALBUM_ENTRY_THUMBNAIL:
		case ALBUM_ENTRY_THUMBNAIL_ALIAS:

			final File thumbnail = ThumbnailUriParser.parseUri(uri, new ThumbnailUriReceiver<File>() {
				@Override
				public File execute(final String archiveName, final String albumId, final String thumbnailId) {
					return getService().getLoadedThumbnail(archiveName, albumId, thumbnailId);
				}
			});
			if (thumbnail == null) {
				throw new FileNotFoundException("Thumbnail-Image " + uri + " not found");
			}
			return ParcelFileDescriptor.open(thumbnail, ParcelFileDescriptor.MODE_READ_ONLY);
		default:
			break;
		}
		return super.openFile(uri, mode);
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
		final long startTime = System.currentTimeMillis();
		try {
			// Log.i(TAG, "Query called: " + uri);

			final Criterium criterium = Criterium.decodeString(selection);
			final SortOrder order = SortOrder.decodeString(sortOrder);

			final List<String> segments = uri.getPathSegments();
			switch (matcher.match(uri)) {
			case ALBUM_LIST:
				return getService().readAlbumList(projection, criterium, order);
			case ALBUM:
				return getService().readSingleAlbum(segments.get(1), segments.get(2), projection, criterium, order);
			case ALBUM_ENTRY_LIST:
				return getService().readAlbumEntryList(segments.get(1), segments.get(2), projection, criterium, order);
			case ALBUM_ENTRY:
				return getService().readSingleAlbumEntry(segments.get(1), segments.get(2), segments.get(4), projection, criterium, order);
			case SERVER_LIST:
				return getService().readServerList(projection, criterium, order);
			case SERVER_PROGRESS_LIST:
				return getService().readServerProgresList(segments.get(1), projection, criterium, order);
			case SERVER_ISSUE_LIST:
				return getService().readServerIssueList(segments.get(1), projection, criterium, order);
			case KEYWORD:
				return getService().readKeywordStatistics(projection, criterium, order);
			case STORAGE_LIST:
				return getService().readStorages(projection, criterium, order);
			case ALBUM_ENTRY_THUMBNAIL:
			case ALBUM_ENTRY_THUMBNAIL_ALIAS:
				return null;
			}
			throw new UnsupportedOperationException("Query of " + uri + " is not supported");
		} catch (final Throwable e) {
			throw new RuntimeException("Cannot query for " + uri, e);
		} finally {
			Log.i(TAG, "Query for " + uri + " took " + (System.currentTimeMillis() - startTime));
		}
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
		Log.i(TAG, "Update called: " + uri);
		final List<String> segments = uri.getPathSegments();
		switch (matcher.match(uri)) {
		case ALBUM:
			return getService().updateAlbum(segments.get(1), segments.get(2), values);
		case ALBUM_ENTRY:
			return getService().updateAlbumEntry(segments.get(1), segments.get(2), segments.get(4), values);
		case ALBUM_ENTRY_LIST:
		case ALBUM_ENTRY_THUMBNAIL:
		case ALBUM_ENTRY_THUMBNAIL_ALIAS:
		case ALBUM_LIST:
		case SERVER_LIST:
		case SERVER_PROGRESS_LIST:
		case SERVER_ISSUE_LIST:
		case STORAGE_LIST:
		case KEYWORD:
		}
		throw new UnsupportedOperationException("Update of " + uri + " is not supported");

	}

	protected Cursor getEmptyCursor(final Class<?> klass) {
		synchronized (emptyCursors) {
			final NotifyableMatrixCursor existingEntry = emptyCursors.get(klass);
			if (existingEntry != null) {
				return existingEntry;
			}
			final ArrayList<String> columns = new ArrayList<String>();
			for (final Field field : klass.getDeclaredFields()) {
				final int modifiers = field.getModifiers();
				if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
					continue;
				}
				if (!String.class.equals(field.getType())) {
					continue;
				}
				try {
					columns.add((String) field.get(null));
				} catch (final Throwable e) {
					throw new RuntimeException("Cannot read static field " + field, e);
				}
			}
			final NotifyableMatrixCursor newCursor = new NotifyableMatrixCursor(columns.toArray(new String[columns.size()]));
			emptyCursors.put(klass, newCursor);
			return newCursor;
		}
	}

	private SynchronisationService getService() {
		if (service == null) {
			return new SynchronisationService() {

				@Override
				public void createAlbumOnServer(final String server, final String fullAlbumName, final Date autoAddDate) {
					// TODO Auto-generated method stub

				}

				@Override
				public String getContenttype(final String archive, final String albumId, final String image) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public File getLoadedThumbnail(final String archiveName, final String albumName, final String albumEntryName) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public Cursor readAlbumEntryList(final String archiveName, final String albumName, final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.AlbumEntry.class);
				}

				@Override
				public Cursor readAlbumList(final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.Album.class);
				}

				@Override
				public Cursor readKeywordStatistics(final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.KeywordEntry.class);
				}

				@Override
				public Cursor readServerIssueList(final String server, final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.IssueEntry.class);
				}

				@Override
				public Cursor readServerList(final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.ServerEntry.class);
				}

				@Override
				public Cursor readServerProgresList(final String server, final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.ProgressEntry.class);
				}

				@Override
				public Cursor readSingleAlbum(final String archiveName, final String albumName, final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.Album.class);
				}

				@Override
				public Cursor readSingleAlbumEntry(	final String archiveName,
																						final String albumName,
																						final String albumEntryName,
																						final String[] projection,
																						final Criterium criterium,
																						final SortOrder order) {
					return getEmptyCursor(Client.AlbumEntry.class);
				}

				@Override
				public Cursor readStorages(final String[] projection, final Criterium criterium, final SortOrder order) {
					return getEmptyCursor(Client.Storage.class);
				}

				@Override
				public int updateAlbum(final String archiveName, final String albumName, final ContentValues values) {
					// TODO Auto-generated method stub
					return 0;
				}

				@Override
				public int updateAlbumEntry(final String archiveName, final String albumName, final String albumEntryName, final ContentValues values) {
					// TODO Auto-generated method stub
					return 0;
				}
			};
		}
		return service;
	}

	private void setService(final SynchronisationService service) {
		this.service = service;
		for (final NotifyableMatrixCursor cursor : emptyCursors.values()) {
			cursor.onChange(false);
		}
	}
}
