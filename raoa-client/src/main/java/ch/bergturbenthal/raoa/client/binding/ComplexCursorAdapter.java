/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Collection;

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

	private final CursorViewBinder viewBinder;

	/**
	 * @param context
	 * @param layout
	 * @param handlers
	 */
	@SuppressWarnings("unchecked")
	public ComplexCursorAdapter(final Context context, final int layout, final Collection<ViewHandler<? extends View>> handlers) {
		super(context, layout, null, true);
		viewBinder = new CursorViewBinder((Collection<ViewHandler<View>>) (Collection<?>) handlers);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		viewBinder.bindView(view, context, cursor);
	}

	/**
	 * collect all required fields on all handlers
	 * 
	 * @return collected fields
	 */
	public String[] requiredFields() {
		return viewBinder.requiredFields();
	}

	@Override
	public Cursor swapCursor(final Cursor newCursor) {
		viewBinder.setCursor(newCursor);
		return super.swapCursor(newCursor);
	}

}
