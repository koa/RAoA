package ch.bergturbenthal.raoa.client.photo;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter.ViewBinder;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.PhotoBinder;

public class PhotoDetailviewAdapter extends PagerAdapter {

	private final Context context;
	private int currentPosition;
	private Cursor cursor = null;
	private final LayoutInflater inflater;
	private final ViewBinder viewBinder;

	public PhotoDetailviewAdapter(final Context context) {
		this.context = context;
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		// set photo detail view binder
		viewBinder = new PhotoBinder(true, context);
	}

	@Override
	public void destroyItem(final View container, final int position, final Object object) {
		((ViewPager) container).removeView((View) object);
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
		final View view = inflater.inflate(R.layout.photo_detailview_item, null);
		final View imageView = view.findViewById(R.id.photos_item_image);
		viewBinder.setViewValue(imageView, cursor, 0);
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
		if (cursor == c) {
			return;
		}

		this.cursor = c;
		notifyDataSetChanged();
	}

}
