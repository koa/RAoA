package ch.bergturbenthal.raoa.client.photo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.PhotoBinder;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoOverviewAdapter extends SimpleCursorAdapter {

	private static final String[] FROM = new String[] { Client.AlbumEntry.THUMBNAIL, Client.AlbumEntry.NAME };

	private static final int[] TO = new int[] { R.id.photos_item_image, R.id.photo_name };

	public PhotoOverviewAdapter(final Context context, final int layout) {
		super(context, layout, null, FROM, TO, 0);

		// set photo overview view binder
		setViewBinder(new PhotoBinder(false, context));
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final View newView = super.getView(position, convertView, parent);
		newView.setTag(getCursor().getString(getCursor().getColumnIndexOrThrow(Client.AlbumEntry.ENTRY_URI)));
		return newView;
	}

}
