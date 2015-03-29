/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.view.View;

/**
 * TODO: add type comment.
 *
 */
public class CursorViewBinder {
	private static Object getColumnValue(final Cursor cursor, final int index) {
		final int type = cursor.getType(index);
		switch (type) {
		case Cursor.FIELD_TYPE_NULL:
			return null;
		case Cursor.FIELD_TYPE_BLOB:
			return cursor.getBlob(index);
		case Cursor.FIELD_TYPE_FLOAT:
			return Double.valueOf(cursor.getDouble(index));
		case Cursor.FIELD_TYPE_INTEGER:
			return Long.valueOf(cursor.getLong(index));
		case Cursor.FIELD_TYPE_STRING:
			return cursor.getString(index);
		}
		throw new RuntimeException("Unknown type " + type);
	}

	private final Map<String, Integer>	        columnIndizes	= new HashMap<String, Integer>();
	private Cursor	                            cursor;

	private final Collection<ViewHandler<View>>	handlers;

	public CursorViewBinder(final Collection<ViewHandler<View>> handlers) {
		this.handlers = handlers;
	}

	public void bindView(final View view, final Context context, final Cursor cursor) {

		for (final ViewHandler<View> handler : handlers) {
			final int[] viewIds = handler.affectedViews();
			final View[] foundViews = new View[viewIds.length];
			for (int i = 0; i < viewIds.length; i++) {
				foundViews[i] = view.findViewById(viewIds[i]);
			}
			final Map<String, Object> fieldsMap = makeMapForFields(cursor, handler.usedFields());
			handler.bindView(foundViews, context, fieldsMap);
		}
	}

	public Object getValueOfColumn(final Cursor cursor, final String fieldName) {
		final int index = columnIndizes.get(fieldName).intValue();
		final Object columnValue = getColumnValue(cursor, index);
		return columnValue;
	}

	private Map<String, Object> makeMapForFields(final Cursor cursor, final String[] usedFields) {
		final HashMap<String, Object> ret = new HashMap<String, Object>();
		for (final String fieldName : usedFields) {
			final Object columnValue = getValueOfColumn(cursor, fieldName);
			ret.put(fieldName, columnValue);
		}
		return ret;
	}

	/**
	 * collect all required fields on all handlers
	 * 
	 * @return collected fields
	 */
	public String[] requiredFields() {
		final Collection<String> ret = new HashSet<String>();
		ret.add("_id");
		for (final ViewHandler<?> handler : handlers) {
			for (final String field : handler.usedFields()) {
				ret.add(field);
			}
		}
		return ret.toArray(new String[ret.size()]);
	}

	public void setCursor(final Cursor c) {
		this.cursor = c;
		columnIndizes.clear();
		if (cursor != null) {
			final String[] columnNames = cursor.getColumnNames();
			for (int i = 0; i < columnNames.length; i++) {
				columnIndizes.put(columnNames[i], Integer.valueOf(i));
			}
		}
	}

}
