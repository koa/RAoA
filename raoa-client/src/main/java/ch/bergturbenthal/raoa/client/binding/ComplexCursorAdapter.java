/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ResourceCursorAdapter;

/**
 * TODO: add type comment.
 * 
 */
public class ComplexCursorAdapter extends ResourceCursorAdapter {
	/**
	 * Register a new {@link Cursor} on a given {@link LoaderManager}
	 * 
	 * @param loaderManager
	 *          loadermanager to register
	 * @param context
	 *          context of the whole thing
	 * @param uri
	 *          uri to query the cursor
	 * @param layout
	 *          Layout of a adapter-element
	 * @param handlers
	 *          handler to fill dynamic elements on the layout
	 * @return
	 */
	public static ComplexCursorAdapter registerLoaderManager(	final LoaderManager loaderManager,
																														final Context context,
																														final Uri uri,
																														final int layout,
																														final Collection<ViewHandler<? extends View>> handlers) {
		return registerLoaderManager(loaderManager, context, uri, null, null, null, layout, handlers);

	}

	private static ComplexCursorAdapter registerLoaderManager(final LoaderManager loaderManager,
																														final Context context,
																														final Uri uri,
																														final String selection,
																														final String[] selectionArgs,
																														final String sortOrder,
																														final int layout,
																														final Collection<ViewHandler<? extends View>> handlers) {
		final ComplexCursorAdapter adapter = new ComplexCursorAdapter(context, layout, handlers);
		loaderManager.initLoader(0, null, new LoaderCallbacks<Cursor>() {

			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(context, uri, adapter.requiredFields(), selection, selectionArgs, sortOrder);
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.swapCursor(null);
			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				adapter.swapCursor(data);
			}
		});
		return adapter;
	}

	private final Map<String, Integer> columnIndizes = new HashMap<String, Integer>();

	private final Collection<ViewHandler<View>> handlers;

	/**
	 * @param context
	 * @param layout
	 * @param handlers
	 */
	public ComplexCursorAdapter(final Context context, final int layout, final Collection<ViewHandler<? extends View>> handlers) {
		super(context, layout, null, true);
		this.handlers = (Collection<ViewHandler<View>>) (Collection<?>) handlers;

	}

	@Override
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

	@Override
	public Cursor swapCursor(final Cursor newCursor) {
		columnIndizes.clear();
		if (newCursor != null) {
			final String[] columnNames = newCursor.getColumnNames();
			for (int i = 0; i < columnNames.length; i++) {
				columnIndizes.put(columnNames[i], Integer.valueOf(i));
			}
		}
		return super.swapCursor(newCursor);
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
