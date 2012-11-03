package ch.bergturbenthal.image.provider;

import java.io.FileNotFoundException;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.util.EnumUriMatcher;
import ch.bergturbenthal.image.provider.util.Path;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;

public class RoyalArchiveContentProvider extends ContentProvider {
  public static enum UriType {
    @Path("albums")
    ALBUM_LIST,
    @Path("albums/#")
    ALBUM_ENTRY,
    @Path("albums/#/thumbnail")
    THUMBNAIL_ENTRY
  }

  private static final String TAG = "Content Provider";

  private static final EnumUriMatcher<UriType> matcher = new EnumUriMatcher<UriType>(Data.AUTHORITY, UriType.class);
  private final ThreadLocal<DaoHolder> transactionManager = new ThreadLocal<DaoHolder>() {

    @Override
    protected DaoHolder initialValue() {
      return new DaoHolder(connectionSource);
    }
  };
  private ConnectionSource connectionSource;

  @Override
  public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
    throw new UnsupportedOperationException("delete not supported");
  }

  @Override
  public String getType(final Uri uri) {
    Log.i(TAG, "getType called");
    switch (matcher.match(uri)) {
    case ALBUM_LIST:
      return "vnd.android.cursor.dir/vnd." + Data.AUTHORITY + "/album";
    case ALBUM_ENTRY:
      return "vnd.android.cursor.item/vnd." + Data.AUTHORITY + "/album";
    case THUMBNAIL_ENTRY:
      return "image/jpeg";
    }
    throw new SQLException("Unknown Uri: " + uri);
  }

  @Override
  public Uri insert(final Uri uri, final ContentValues values) {
    throw new UnsupportedOperationException("insert not supported");
  }

  @Override
  public boolean onCreate() {
    connectionSource = DatabaseHelper.makeConnectionSource(getContext());
    Log.i(TAG, "Content-Provider created");
    return true;
  }

  @Override
  public ParcelFileDescriptor openFile(final Uri uri, final String mode) throws FileNotFoundException {
    // TODO Auto-generated method stub
    return super.openFile(uri, mode);
  }

  @Override
  public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
    Log.i(TAG, "Query called: " + uri);
    final UriType type = matcher.match(uri);
    Log.i(TAG, "Type: " + type);
    switch (type) {
    case ALBUM_LIST:
      final RuntimeExceptionDao<AlbumEntity, String> albumDao = transactionManager.get().getDao(AlbumEntity.class);
      final List<AlbumEntity> albums = albumDao.queryForAll();
      final MatrixCursor matrixCursor = new MatrixCursor(new String[] { Data.Album.ID, Data.Album.NAME });
      for (final AlbumEntity albumEntity : albums) {
        matrixCursor.addRow(new Object[] { albumEntity.getId(), albumEntity.getName() });
      }
      return matrixCursor;

    default:
      break;
    }
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
    // TODO Auto-generated method stub
    return 0;
  }

}
