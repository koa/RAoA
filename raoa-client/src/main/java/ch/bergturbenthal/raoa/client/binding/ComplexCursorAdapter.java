/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

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
	 * @param additionalColumns
	 *          additional columns which can be read via {@link ComplexCursorAdapter#getAdditionalValues(int)}
	 * @return ComplexCursorAdapter
	 */
	public static ComplexCursorAdapter registerLoaderManager(	final LoaderManager loaderManager,
																														final Context context,
																														final Uri uri,
																														final int layout,
																														final Collection<ViewHandler<? extends View>> handlers,
																														final String[] additionalColumns) {
		return registerLoaderManager(loaderManager, context, uri, null, null, null, layout, handlers, additionalColumns);

	}

	private static ComplexCursorAdapter registerLoaderManager(final LoaderManager loaderManager,
																														final Context context,
																														final Uri uri,
																														final String selection,
																														final String[] selectionArgs,
																														final String sortOrder,
																														final int layout,
																														final Collection<ViewHandler<? extends View>> handlers,
																														final String[] additionalColumns) {
		final ComplexCursorAdapter adapter = new ComplexCursorAdapter(context, layout, handlers, additionalColumns);
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

	private final String[] additionalColumns;
	private final CursorViewBinder viewBinder;

	/**
	 * @param context
	 * @param layout
	 * @param handlers
	 * @param additionalColumns
	 *          TODO
	 */
	@SuppressWarnings("unchecked")
	public ComplexCursorAdapter(final Context context, final int layout, final Collection<ViewHandler<? extends View>> handlers, final String[] additionalColumns) {
		super(context, layout, null, true);
		viewBinder = new CursorViewBinder((Collection<ViewHandler<View>>) (Collection<?>) handlers);
		this.additionalColumns = additionalColumns;
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		viewBinder.bindView(view, context, cursor);
	}

	public Object[] getAdditionalValues(final int position) {
		if (additionalColumns == null) {
			return null;
		}
		final Cursor cursor = getCursor();
		cursor.moveToPosition(position);
		final Object[] ret = new Object[additionalColumns.length];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = viewBinder.getValueOfColumn(cursor, additionalColumns[i]);
		}
		return ret;
	}

	/**
	 * collect all required fields on all handlers
	 * 
	 * @return collected fields
	 */
	public String[] requiredFields() {
		final HashSet<String> fields = new HashSet<String>();
		fields.addAll(Arrays.asList(viewBinder.requiredFields()));
		if (additionalColumns != null) {
			fields.addAll(Arrays.asList(additionalColumns));
		}
		fields.add("_id");
		return fields.toArray(new String[fields.size()]);
	}

	@Override
	public Cursor swapCursor(final Cursor newCursor) {
		viewBinder.setCursor(newCursor);
		return super.swapCursor(newCursor);
	}

}
