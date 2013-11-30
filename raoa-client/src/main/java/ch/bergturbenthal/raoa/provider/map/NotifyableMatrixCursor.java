package ch.bergturbenthal.raoa.provider.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.database.AbstractCursor;

public class NotifyableMatrixCursor extends AbstractCursor {
	public static interface SingleFieldReader {
		Number getNumber();

		String getString();

		int getType();

		Object getValue();

		boolean isNull();
	}

	int columnCount = 0;
	private final String[] columnNames;
	private final List<SingleFieldReader> valueReaders = new ArrayList<NotifyableMatrixCursor.SingleFieldReader>();

	public NotifyableMatrixCursor(final String[] columnNames) {
		this.columnNames = columnNames;
	}

	public void addRow(final SingleFieldReader[] entryValues) {
		assert entryValues.length == columnNames.length;
		valueReaders.addAll(Arrays.asList(entryValues));
		columnCount++;
	}

	@Override
	public String[] getColumnNames() {
		return columnNames;
	}

	@Override
	public int getCount() {
		return columnCount;
	}

	@Override
	public double getDouble(final int column) {
		return getReader(column).getNumber().doubleValue();
	}

	@Override
	public float getFloat(final int column) {
		return getReader(column).getNumber().floatValue();
	}

	@Override
	public int getInt(final int column) {
		return getReader(column).getNumber().intValue();
	}

	@Override
	public long getLong(final int column) {
		return getReader(column).getNumber().longValue();
	}

	private SingleFieldReader getReader(final int column) {
		return valueReaders.get(mPos * columnNames.length + column);
	}

	@Override
	public short getShort(final int column) {
		return getReader(column).getNumber().shortValue();
	}

	@Override
	public String getString(final int column) {
		return getReader(column).getString();
	}

	@Override
	public int getType(final int column) {
		return getReader(column).getType();
	}

	@Override
	public boolean isNull(final int column) {
		return getReader(column).isNull();
	}

	@Override
	public void onChange(final boolean selfChange) {
		super.onChange(selfChange);
	}
}
