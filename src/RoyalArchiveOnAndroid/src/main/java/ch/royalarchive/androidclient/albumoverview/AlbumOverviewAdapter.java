package ch.royalarchive.androidclient.albumoverview;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;

public class AlbumOverviewAdapter extends SimpleCursorAdapter {

	private static final String[] FROM = new String[] { 
		Client.Album.NAME, 
		Client.Album.ENTRY_COUNT,
		Client.Album.THUMBNAIL };

	private static final int[] TO = new int[] {
		R.id.album_item_name, 
		R.id.album_item_size, 
		R.id.album_item_image };

	public AlbumOverviewAdapter(Context context, int layout, Cursor cursor) {
		super(context, layout, cursor, FROM, TO, 0);

		// set album overview view binder
		setViewBinder(new AlbumOverviewViewBinder());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View newView = super.getView(position, convertView, parent);
		newView.setTag(getCursor().getInt(getCursor().getColumnIndexOrThrow(Client.Album.ID)));
		return newView;
	}

}
