package ch.bergturbenthal.raoa.provider.map;

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
		if (value == null)
			return null;
		return getString(value);
	}

	@Override
	public boolean isNull(final V value) {
		return value == null || getString(value) == null;
	}
}