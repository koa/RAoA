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
	private final Map<String, Integer> columnIndizes = new HashMap<String, Integer>();
	private Cursor cursor;
	private final Collection<ViewHandler<View>> handlers;

	public CursorViewBinder(final Collection<ViewHandler<View>> handlers) {
		this.handlers = handlers;
	}

	public void bindView(final View view, final Context context, final Cursor cursor) {

		for (final ViewHandler<View> handler : handlers) {
			final View foundView = view.findViewById(handler.affectedView());
			final Map<String, Object> fieldsMap = makeMapForFields(cursor, handler.usedFields());
			handler.bindView(foundView, context, fieldsMap);
		}
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

	private Map<String, Object> makeMapForFields(final Cursor cursor, final String[] usedFields) {
		final HashMap<String, Object> ret = new HashMap<String, Object>();
		for (final String fieldName : usedFields) {
			final int index = columnIndizes.get(fieldName).intValue();
			switch (cursor.getType(index)) {
			case Cursor.FIELD_TYPE_NULL:
				ret.put(fieldName, null);
				break;
			case Cursor.FIELD_TYPE_BLOB:
				ret.put(fieldName, cursor.getBlob(index));
				break;
			case Cursor.FIELD_TYPE_FLOAT:
				ret.put(fieldName, Double.valueOf(cursor.getDouble(index)));
				break;
			case Cursor.FIELD_TYPE_INTEGER:
				ret.put(fieldName, Long.valueOf(cursor.getLong(index)));
				break;
			case Cursor.FIELD_TYPE_STRING:
				ret.put(fieldName, cursor.getString(index));
				break;
			}
		}
		return ret;
	}

}
