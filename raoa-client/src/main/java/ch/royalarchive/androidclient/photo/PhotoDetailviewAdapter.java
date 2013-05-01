package ch.royalarchive.androidclient.photo;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import ch.royalarchive.androidclient.PhotoBinder;

public class PhotoDetailviewAdapter extends PagerAdapter {
	
	private final Context context;
	private final ViewBinder viewBinder;
	private Cursor cursor;
	private int currentPosition;

	public PhotoDetailviewAdapter(Context context, Cursor cursor) {
		this.context = context;
		this.cursor = cursor;

		// set photo detail view binder
		viewBinder = new PhotoBinder(true, context);
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		ImageView view = new ImageView(context);
		cursor.moveToPosition(position);
		viewBinder.setViewValue(view, cursor, 0);
		container.addView(view);
		return view;
	}

	@Override
	public void destroyItem(View container, int position, Object object) {
		((ViewPager) container).removeView((View) object);
	}
	
	@Override
	public void setPrimaryItem(View container, int position, Object object) {
		currentPosition = position;
	}

	@Override
	public int getCount() {
		if (cursor == null)
			return 0;
		else
			return cursor.getCount();
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return (view == object);
	}

	public void swapCursor(Cursor c) {
		if (cursor == c)
			return;

		this.cursor = c;
		notifyDataSetChanged();
	}

	public int getCurrentPosition() {
		return currentPosition;
	}

}
