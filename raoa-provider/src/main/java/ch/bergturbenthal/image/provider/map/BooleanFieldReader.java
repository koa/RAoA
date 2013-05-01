package ch.bergturbenthal.image.provider.map;

import android.database.Cursor;

public abstract class BooleanFieldReader<V> extends NumericFieldReader<V> {
	public BooleanFieldReader() {
		super(Cursor.FIELD_TYPE_INTEGER);
	}

	public abstract Boolean getBooleanValue(final V value);

	@Override
	public Number getNumber(final V value) {
		final Boolean booleanValue = getBooleanValue(value);
		if (booleanValue == null)
			return null;
		return Integer.valueOf(booleanValue.booleanValue() ? 1 : 0);
	}
}