package ch.bergturbenthal.raoa.client.album;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.PhotoBinder;
import ch.bergturbenthal.raoa.provider.Client;

public class AlbumOverviewAdapter extends SimpleCursorAdapter {

	private static final String[] FROM = new String[] { Client.Album.NAME, Client.Album.ENTRY_COUNT, Client.Album.THUMBNAIL };

	private static final int[] TO = new int[] { R.id.album_item_name, R.id.album_item_size, R.id.album_item_image };

	private final Context context;

	public AlbumOverviewAdapter(final Context context, final int layout) {
		super(context, layout, null, FROM, TO, 0);
		this.context = context;
		// set album overview view binder
		setViewBinder(new PhotoBinder(false, context));
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final ViewGroup newView = (ViewGroup) super.getView(position, convertView, parent);
		final String childrenEntriesUri = getCursor().getString(getCursor().getColumnIndexOrThrow(Client.Album.ALBUM_ENTRIES_URI));
		final String entryUri = getCursor().getString(getCursor().getColumnIndexOrThrow(Client.Album.ENTRY_URI));
		newView.setTag(childrenEntriesUri);
		final View offlineIcon = newView.findViewById(R.id.album_item_icon_offline);
		offlineIcon.setTag(entryUri);
		offlineIcon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				final ContentValues values = new ContentValues();
				values.put(Client.Album.SHOULD_SYNC, Boolean.TRUE);
				context.getContentResolver().update(Uri.parse(entryUri), values, null, null);
			}
		});
		return newView;
	}

}
