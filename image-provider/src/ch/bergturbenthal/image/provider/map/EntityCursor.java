package ch.bergturbenthal.image.provider.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.util.Log;

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.stmt.QueryBuilder;

public class EntityCursor<V, P> extends AbstractCursor {
  private static final String TAG = "EntityCursor";
  private final String[] queryingProjection;
  private final QueryBuilder<V, P> queryBuilder;
  private final FieldReader<V>[] fieldReaders;
  private final CloseableIterator<V> dataIterator;
  private V currentEntry = null;
  private int calculatedCount = -1;

  public EntityCursor(final QueryBuilder<V, P> queryBuilder, final String[] queryingProjection, final Map<String, FieldReader<V>> fieldReaders)

  throws java.sql.SQLException {

    final List<String> queryingProjectionList = new ArrayList<String>();
    final List<FieldReader<V>> fieldReaderList = new ArrayList<FieldReader<V>>();
    final List<String> fieldList = queryingProjection == null ? new ArrayList<String>(fieldReaders.keySet()) : Arrays.asList(queryingProjection);
    for (final String columnName : fieldList) {
      final FieldReader<V> fieldReader = fieldReaders.get(columnName);
      if (fieldReader == null) {
        Log.i(TAG, "no field Reader for field " + columnName);
        continue;
      }
      fieldReaderList.add(fieldReader);
      queryingProjectionList.add(columnName);
    }

    this.queryingProjection = queryingProjectionList.toArray(new String[queryingProjectionList.size()]);
    this.fieldReaders = fieldReaderList.toArray(new FieldReader[fieldReaderList.size()]);
    this.queryBuilder = queryBuilder;
    this.dataIterator = queryBuilder.iterator();
    currentEntry = dataIterator.current();
  }

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
    return getFieldReader(column).getNumber(currentEntry).doubleValue();
  }

  @Override
  public float getFloat(final int column) {
    return getFieldReader(column).getNumber(currentEntry).floatValue();
  }

  @Override
  public int getInt(final int column) {
    return getFieldReader(column).getNumber(currentEntry).intValue();
  }

  @Override
  public long getLong(final int column) {
    return getFieldReader(column).getNumber(currentEntry).longValue();
  }

  @Override
  public short getShort(final int column) {
    return getFieldReader(column).getNumber(currentEntry).shortValue();
  }

  @Override
  public String getString(final int column) {
    return getFieldReader(column).getString(currentEntry);
  }

  @Override
  public int getType(final int column) {
    final FieldReader<V> fieldReader = getFieldReader(column);
    if (fieldReader.isNull(currentEntry))
      return Cursor.FIELD_TYPE_NULL;
    return fieldReader.getType();
  }

  @Override
  public boolean isNull(final int column) {
    return getFieldReader(column).isNull(currentEntry);
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

  private FieldReader<V> getFieldReader(final int column) {
    return fieldReaders[column];
  }
}