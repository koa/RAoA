package ch.bergturbenthal.image.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;

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
import ch.bergturbenthal.image.provider.service.SynchronisationService;
import ch.bergturbenthal.image.provider.service.SynchronisationServiceImpl;
import ch.bergturbenthal.image.provider.service.SynchronisationServiceImpl.LocalBinder;
import ch.bergturbenthal.image.provider.util.EnumUriMatcher;
import ch.bergturbenthal.image.provider.util.Path;

public class ArchiveContentProvider extends ContentProvider {
  public static enum UriType {
    @Path("albums")
    ALBUM_LIST,
    @Path("albums/*/*")
    ALBUM,
    @Path("albums/*/*/entries")
    ALBUM_ENTRY_LIST,
    @Path("albums/*/*/entries/*")
    ALBUM_ENTRY,
    @Path("albums/*/*/entries/*/thumbnail")
    ALBUM_ENTRY_THUMBNAIL,
    @Path("servers")
    SERVER_LIST,
    @Path("servers/*/progress")
    SERVER_PROGRESS_LIST,
    @Path("servers/*/issues")
    SERVER_ISSUE_LIST

  }

  static final String TAG = "Content Provider";

  private static final EnumUriMatcher<UriType> matcher = new EnumUriMatcher<UriType>(Client.AUTHORITY, UriType.class);

  private SynchronisationService service = null;
  /** Defines callbacks for service binding, passed to bindService() */
  private final ServiceConnection serviceConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(final ComponentName className, final IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService
      // instance
      final LocalBinder binder = (LocalBinder) service;
      ArchiveContentProvider.this.service = binder.getService();
    }

    @Override
    public void onServiceDisconnected(final ComponentName arg0) {
      service = null;
    }
  };

  @Override
  public Bundle call(final String method, final String arg, final Bundle extras) {
    if (Client.METHOD_CREATE_ALBUM_ON_SERVER.equals(method)) {
      final String server = arg;
      final String fullAlbumName = extras.getString(Client.PARAMETER_FULL_ALBUM_NAME);
      final Date autoAddDate = extras.containsKey(Client.PARAMETER_AUTOADD_DATE) ? new Date(extras.getLong(Client.PARAMETER_AUTOADD_DATE)) : null;
      service.createAlbumOnServer(server, fullAlbumName, autoAddDate);
    }
    return null;
  }

  @Override
  public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
    throw new UnsupportedOperationException("delete not supported");
  }

  @Override
  public String getType(final Uri uri) {
    Log.i(TAG, "getType called");
    switch (matcher.match(uri)) {
    case ALBUM_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/album";
    case ALBUM:
      return "vnd.android.cursor.item/vnd." + Client.AUTHORITY + "/album";
    case ALBUM_ENTRY_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/album/entry";
    case ALBUM_ENTRY:
      return "vnd.android.cursor.item/vnd." + Client.AUTHORITY + "/album/entry";
    case ALBUM_ENTRY_THUMBNAIL: {
      final List<String> segments = uri.getPathSegments();
      final String archive = segments.get(1);
      final String albumId = segments.get(2);
      final String image = segments.get(4);
      return service.getContenttype(archive, albumId, image);
    }
    case SERVER_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server";
    case SERVER_PROGRESS_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server/progress";
    case SERVER_ISSUE_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server/issues";
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
    Log.i(TAG, "Open called for " + uri);
    final UriType match = matcher.match(uri);
    if (match == null)
      return super.openFile(uri, mode);
    switch (match) {
    case ALBUM_ENTRY_THUMBNAIL:
      final List<String> segments = uri.getPathSegments();
      final String archive = segments.get(1);
      final String albumId = segments.get(2);
      final String image = segments.get(4);
      Log.i(TAG, "Selected Entry: " + archive + ":" + albumId + ":" + image);
      final File thumbnail = service.getLoadedThumbnail(archive, albumId, image);
      if (thumbnail == null)
        throw new FileNotFoundException("Thumbnail-Image " + uri + " not found");
      return ParcelFileDescriptor.open(thumbnail, ParcelFileDescriptor.MODE_READ_ONLY);
    default:
      break;
    }
    return super.openFile(uri, mode);
  }

  @Override
  public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
    try {
      Log.i(TAG, "Query called: " + uri);
      final List<String> segments = uri.getPathSegments();
      switch (matcher.match(uri)) {
      case ALBUM_LIST:
        return service.readAlbumList(projection);
      case ALBUM:
        return service.readSingleAlbum(segments.get(1), segments.get(2), projection);
      case ALBUM_ENTRY_LIST:
        return service.readAlbumEntryList(segments.get(1), segments.get(2), projection);
      case ALBUM_ENTRY:
        return service.readSingleAlbumEntry(segments.get(1), segments.get(2), segments.get(4), projection);
      case SERVER_LIST:
        return service.readServerList(projection);
      case SERVER_PROGRESS_LIST:
        return service.readServerProgresList(segments.get(1), projection);
      case SERVER_ISSUE_LIST:
        return service.readServerIssueList(segments.get(1), projection);
      case ALBUM_ENTRY_THUMBNAIL:
      }
      throw new UnsupportedOperationException("Query of " + uri + " is not supported");
    } catch (final Throwable e) {
      throw new RuntimeException("Cannot query for " + uri, e);
    }
  }

  @Override
  public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
    Log.i(TAG, "Update called: " + uri);
    final List<String> segments = uri.getPathSegments();
    switch (matcher.match(uri)) {
    case ALBUM:
      return service.updateAlbum(segments.get(1), segments.get(2), values);
    case ALBUM_ENTRY:
      return service.updateAlbumEntry(segments.get(1), segments.get(2), segments.get(4), values);
    case ALBUM_ENTRY_LIST:
    case ALBUM_ENTRY_THUMBNAIL:
    case ALBUM_LIST:
    case SERVER_LIST:
    case SERVER_PROGRESS_LIST:
    case SERVER_ISSUE_LIST:
    }
    throw new UnsupportedOperationException("Update of " + uri + " is not supported");
  }
}
