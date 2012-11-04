package ch.bergturbenthal.image.provider;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.util.EnumUriMatcher;
import ch.bergturbenthal.image.provider.util.Path;

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;

public class ArchiveContentProvider extends ContentProvider {
  public abstract class BooleanFieldReader<V> extends NumericFieldReader<V> {
    public BooleanFieldReader() {
      super(Cursor.FIELD_TYPE_INTEGER);
      // TODO Auto-generated constructor stub
    }

    public abstract Boolean getBooleanValue(V value);

    @Override
    public Number getNumber(final V value) {
      final Boolean booleanValue = getBooleanValue(value);
      if (booleanValue == null)
        return null;
      return Integer.valueOf(booleanValue.booleanValue() ? 1 : 0);
    }
  }

  public static interface FieldReader<V> {
    Number getNumber(V value);

    String getString(V value);

    int getType();

    boolean isNull(V value);
  }

  public abstract class NumericFieldReader<V> implements FieldReader<V> {
    final int type;

    public NumericFieldReader(final int type) {
      this.type = type;
    }

    @Override
    public String getString(final V value) {
      return getNumber(value).toString();
    }

    @Override
    public int getType() {
      return type;
    }

    @Override
    public boolean isNull(final V value) {
      return getNumber(value) == null;
    }
  }

  public abstract class StringFieldReader<V> implements FieldReader<V> {
    @Override
    public Number getNumber(final V value) {
      throw new IllegalArgumentException("Cannot read Number of a string field");
    }

    @Override
    public int getType() {
      return Cursor.FIELD_TYPE_STRING;
    }

    @Override
    public boolean isNull(final V value) {
      return getString(value) == null;
    }
  }

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
    try {
      Log.i(TAG, "Query called: " + uri);
      final UriType type = matcher.match(uri);
      Log.i(TAG, "Type: " + type);
      switch (type) {
      case ALBUM_LIST:
        final RuntimeExceptionDao<AlbumEntity, String> albumDao = transactionManager.get().getDao(AlbumEntity.class);
        final QueryBuilder<AlbumEntity, String> queryBuilder = albumDao.queryBuilder();

        final String[] queryingProjection = projection == null ? Data.Album.ALL_COLUMNS : projection;
        final Map<String, FieldReader<AlbumEntity>> fieldReaders = new HashMap<String, ArchiveContentProvider.FieldReader<AlbumEntity>>();
        fieldReaders.put(Data.Album.ARCHIVE_NAME, new StringFieldReader<AlbumEntity>() {
          @Override
          public String getString(final AlbumEntity value) {
            return value.getArchive().getName();
          }
        });
        fieldReaders.put(Data.Album.AUTOADD_DATE, new NumericFieldReader<AlbumEntity>(Cursor.FIELD_TYPE_INTEGER) {
          @Override
          public Number getNumber(final AlbumEntity value) {
            final Date autoAddDate = value.getAutoAddDate();
            if (autoAddDate == null)
              return null;
            return Long.valueOf(autoAddDate.getTime());
          }
        });
        fieldReaders.put(Data.Album.ID, new NumericFieldReader<AlbumEntity>(Cursor.FIELD_TYPE_INTEGER) {
          @Override
          public Number getNumber(final AlbumEntity value) {
            return Integer.valueOf(value.getId());
          }
        });
        fieldReaders.put(Data.Album.NAME, new StringFieldReader<AlbumEntity>() {
          @Override
          public String getString(final AlbumEntity value) {
            return value.getName();
          }
        });
        fieldReaders.put(Data.Album.SHOULD_SYNC, new BooleanFieldReader<AlbumEntity>() {
          @Override
          public Boolean getBooleanValue(final AlbumEntity value) {
            return value.isShouldSync();
          }
        });
        fieldReaders.put(Data.Album.SYNCED, new BooleanFieldReader<AlbumEntity>() {

          @Override
          public Boolean getBooleanValue(final AlbumEntity value) {
            return value.isSynced();
          }
        });

        // final List<AlbumEntity> albums = albumDao.queryForAll();
        // final MatrixCursor matrixCursor = new MatrixCursor(new String[] {
        // Data.Album.ID, Data.Album.NAME, Data.Album.ARCHIVE_NAME });
        // for (final AlbumEntity albumEntity : albums) {
        // matrixCursor.addRow(new Object[] { albumEntity.getId(),
        // albumEntity.getName(), albumEntity.getArchive().getName() });
        // }
        final CloseableIterator<AlbumEntity> dataIterator = queryBuilder.iterator();
        return new AbstractCursor() {

          private AlbumEntity currentEntry = null;
          private int calculatedCount = -1;

          @Override
          public String[] getColumnNames() {
            return queryingProjection;
          }

          @Override
          public synchronized int getCount() {
            if (calculatedCount < 0) {
              try {
                calculatedCount = Integer.parseInt(queryBuilder.setCountOf(true).queryRawFirst()[0]);
              } catch (final Throwable e) {
                throw new RuntimeException("Cannot count results", e);
              }
              // TODO Auto-generated method stub
            }
            return calculatedCount;
          }

          @Override
          public double getDouble(final int column) {
            return fieldReaders.get(queryingProjection[column]).getNumber(currentEntry).doubleValue();
          }

          @Override
          public float getFloat(final int column) {
            return fieldReaders.get(queryingProjection[column]).getNumber(currentEntry).floatValue();
          }

          @Override
          public int getInt(final int column) {
            return fieldReaders.get(queryingProjection[column]).getNumber(currentEntry).intValue();
          }

          @Override
          public long getLong(final int column) {
            return fieldReaders.get(queryingProjection[column]).getNumber(currentEntry).longValue();
          }

          @Override
          public short getShort(final int column) {
            return fieldReaders.get(queryingProjection[column]).getNumber(currentEntry).shortValue();
          }

          @Override
          public String getString(final int column) {
            return fieldReaders.get(queryingProjection[column]).getString(currentEntry);
          }

          @Override
          public int getType(final int column) {
            final FieldReader<AlbumEntity> fieldReader = fieldReaders.get(queryingProjection[column]);
            if (fieldReader.isNull(currentEntry))
              return Cursor.FIELD_TYPE_NULL;
            return fieldReader.getType();
          }

          @Override
          public boolean isNull(final int column) {
            return fieldReaders.get(queryingProjection[column]).isNull(currentEntry);
          }

          @Override
          public boolean onMove(final int oldPosition, final int newPosition) {
            if (oldPosition == newPosition)
              return true;
            if (oldPosition + 1 == newPosition) {
              if (!dataIterator.hasNext())
                return false;
              currentEntry = dataIterator.next();
              return true;
            }
            try {
              currentEntry = dataIterator.moveRelative(newPosition - oldPosition);
              return true;
            } catch (final java.sql.SQLException e) {
              Log.e(TAG, "Cannot move to " + newPosition, e);
            }
            return false;
          }

        };
        // return matrixCursor;

      default:
        break;
      }
      // TODO Auto-generated method stub
      return null;
    } catch (final java.sql.SQLException e) {
      throw new RuntimeException("Cannot query for " + uri, e);
    }
  }

  @Override
  public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
    // TODO Auto-generated method stub
    return 0;
  }

}
