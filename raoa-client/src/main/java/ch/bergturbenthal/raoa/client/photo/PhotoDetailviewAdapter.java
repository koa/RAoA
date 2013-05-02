package ch.bergturbenthal.raoa.client.photo;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import ch.bergturbenthal.raoa.client.PhotoBinder;

public class PhotoDetailviewAdapter extends PagerAdapter {

	private final Context context;
	private int currentPosition;
	private Cursor cursor;
	private final ViewBinder viewBinder;

	public PhotoDetailviewAdapter(final Context context, final Cursor cursor) {
		this.context = context;
		this.cursor = cursor;

		// set photo detail view binder
		viewBinder = new PhotoBinder(true, context);
	}

	@Override
	public void destroyItem(final View container, final int position, final Object object) {
		((ViewPager) container).removeView((View) object);
	}

	@Override
	public int getCount() {
		if (cursor == null)
			return 0;
		else
			return cursor.getCount();
	}

	public int getCurrentPosition() {
		return currentPosition;
	}

	@Override
	public Object instantiateItem(final ViewGroup container, final int position) {
		final ImageView view = new ImageView(context);
		cursor.moveToPosition(position);
		viewBinder.setViewValue(view, cursor, 0);
		container.addView(view);
		return view;
	}

	@Override
	public boolean isViewFromObject(final View view, final Object object) {
		return (view == object);
	}

	@Override
	public void setPrimaryItem(final View container, final int position, final Object object) {
		currentPosition = position;
	}

	public void swapCursor(final Cursor c) {
		if (cursor == c)
			return;

		this.cursor = c;
		notifyDataSetChanged();
	}

}
