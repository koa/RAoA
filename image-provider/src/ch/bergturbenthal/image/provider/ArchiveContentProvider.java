package ch.bergturbenthal.image.provider;

import java.io.File;
import java.io.FileNotFoundException;
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
    @Path("albums/#")
    ALBUM,
    @Path("albums/#/entries")
    ALBUM_ENTRY_LIST,
    @Path("albums/#/entries/#")
    ALBUM_ENTRY,
    @Path("albums/#/entries/#/thumbnail")
    ALBUM_ENTRY_THUMBNAIL,
    @Path("servers")
    SERVER_LIST,
    @Path("servers/#/progress")
    SERVER_PROGRESS_LIST

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
    case ALBUM_ENTRY_THUMBNAIL:
      return "image/jpeg";
    case SERVER_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server";
    case SERVER_PROGRESS_LIST:
      return "vnd.android.cursor.dir/vnd." + Client.AUTHORITY + "/server/progress";
    }
    throw new SQLException("Unknown Uri: " + uri);
  }

  @Override
  public Uri insert(final Uri uri, final ContentValues values) {
    throw new UnsupportedOperationException("insert not supported");
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
      final String album = segments.get(1);
      final String image = segments.get(3);
      Log.i(TAG, "Selected Entry: " + album + ":" + image);
      final File thumbnail = service.getLoadedThumbnail(Integer.parseInt(image));
      if (thumbnail == null)
        throw new FileNotFoundException("Thumbnail-Image " + uri + " not found");
      return ParcelFileDescriptor.open(thumbnail, ParcelFileDescriptor.MODE_READ_ONLY);
    default:
      break;
    }
    // TODO Auto-generated method stub
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
      case ALBUM_ENTRY_LIST:
        return service.readAlbumEntryList(Integer.parseInt(segments.get(1)), projection);
      case SERVER_LIST:
        return service.readServerList(projection);
      case SERVER_PROGRESS_LIST:
        return service.readServerProgresList(segments.get(1), projection);
      default:
        break;
      }
      // TODO Auto-generated method stub
      return null;
    } catch (final Throwable e) {
      throw new RuntimeException("Cannot query for " + uri, e);
    }
  }

  @Override
  public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {

    // TODO Auto-generated method stub
    return 0;
  }

  private Cursor readAlbumList(final String[] projection) {
    return service.readAlbumList(projection);
  }

}
