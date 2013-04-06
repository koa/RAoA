package ch.bergturbenthal.image.provider.map;

import android.database.Cursor;

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
  public Object getValue(final V value) {
    return getString(value);
  }

  @Override
  public boolean isNull(final V value) {
    return getString(value) == null;
  }
}