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
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * TODO: add type comment.
 * 
 */
public class CursorPagerAdapter extends PagerAdapter {
	public static interface CurrentPosProvider {
		int getCurrentPos();
	}

	/**
	 * Register a new {@link Cursor} on a given {@link LoaderManager}
	 * 
	 * @param loaderManager
	 *          loadermanager to register
	 * @param context
	 *          context of the whole thing
	 * @param uri
	 *          uri to query the cursor
	 * @param selection
	 *          selection-String
	 * @param sortOrder
	 *          ordering-string
	 * @param layout
	 *          Layout of a adapter-element
	 * @param handlers
	 *          handler to fill dynamic elements on the layout
	 * @return
	 */
	public static CursorPagerAdapter registerLoaderManager(	final LoaderManager loaderManager,
																													final Context context,
																													final Uri uri,
																													final String selection,
																													final String sortOrder,
																													final int layout,
																													final Collection<ViewHandler<? extends View>> handlers,
																													final String[] additionalColumns) {
		final CursorPagerAdapter adapter = new CursorPagerAdapter(context, layout, handlers, additionalColumns);
		loaderManager.initLoader(0, null, new LoaderCallbacks<Cursor>() {
			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(context, uri, adapter.requiredFields(), selection, null, sortOrder);
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.swapCursor(null);

			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				adapter.swapCursor(data);
				if (adapter.cursorLoadedHandler != null) {
					adapter.cursorLoadedHandler.run();
				}
			}
		});
		return adapter;
	}

	private final String[] additionalColumns;

	private final Context context;
	private int currentPosition;
	private Cursor cursor = null;
	private Runnable cursorLoadedHandler;
	private final LayoutInflater inflater;

	private final int layout;

	private final CursorViewBinder viewBinder;

	public CursorPagerAdapter(final Context context, final int layout, final Collection<ViewHandler<? extends View>> handlers, final String[] additionalColumns) {
		this.context = context;
		this.layout = layout;
		this.additionalColumns = additionalColumns;
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		viewBinder = new CursorViewBinder((Collection<ViewHandler<View>>) (Collection<?>) handlers);
	}

	@Override
	public void destroyItem(final View container, final int position, final Object object) {
		((ViewPager) container).removeView((View) object);
	}

	public Object[] getAdditionalValues(final int position) {
		if (additionalColumns == null) {
			return null;
		}
		if (cursor == null) {
			return null;
		}
		if (!cursor.moveToPosition(position)) {
			return null;
		}
		final Object[] ret = new Object[additionalColumns.length];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = viewBinder.getValueOfColumn(cursor, additionalColumns[i]);
		}
		return ret;
	}

	@Override
	public int getCount() {
		if (cursor == null) {
			return 0;
		} else {
			return cursor.getCount();
		}
	}

	public int getCurrentPosition() {
		return currentPosition;
	}

	@Override
	public View instantiateItem(final ViewGroup container, final int position) {
		cursor.moveToPosition(position);
		final View view = inflater.inflate(layout, null);
		viewBinder.bindView(view, context, cursor);
		container.addView(view);
		return view;
	}

	@Override
	public boolean isViewFromObject(final View view, final Object object) {
		return (view == object);
	}

	/**
	 * Sets the cursorLoadedHandler.
	 * 
	 * @param cursorLoadedHandler
	 *          the cursorLoadedHandler to set
	 */
	public void setCursorLoadedHandler(final Runnable cursorLoadedHandler) {
		this.cursorLoadedHandler = cursorLoadedHandler;
	}

	@Override
	public void setPrimaryItem(final View container, final int position, final Object object) {
		currentPosition = position;
	}

	public void swapCursor(final Cursor c) {
		if (cursor == c) {
			return;
		}
		viewBinder.setCursor(c);

		this.cursor = c;
		notifyDataSetChanged();
	}

	/**
	 * @return
	 */
	protected String[] requiredFields() {
		if (additionalColumns != null && additionalColumns.length > 0) {
			final HashSet<String> requiredFields = new HashSet<String>(Arrays.asList(viewBinder.requiredFields()));
			requiredFields.addAll(Arrays.asList(additionalColumns));
			return requiredFields.toArray(new String[requiredFields.size()]);
		}
		return viewBinder.requiredFields();
	}

}
