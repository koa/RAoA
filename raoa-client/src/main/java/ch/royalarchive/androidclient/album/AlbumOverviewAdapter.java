package ch.royalarchive.androidclient.album;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.PhotoBinder;
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

	private Context context;

	public AlbumOverviewAdapter(Context context, int layout) {
		super(context, layout, null, FROM, TO, 0);
		this.context = context;
		// set album overview view binder
		setViewBinder(new PhotoBinder(false, context));
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewGroup newView = (ViewGroup) super.getView(position, convertView, parent);		
		String childrenEntriesUri = getCursor().getString(getCursor().getColumnIndexOrThrow(Client.Album.ALBUM_ENTRIES_URI));
		final String entryUri = getCursor().getString(getCursor().getColumnIndexOrThrow(Client.Album.ENTRY_URI));
		newView.setTag(childrenEntriesUri);
		View offlineIcon = newView.findViewById(R.id.album_item_icon_offline);
		offlineIcon.setTag(entryUri);
		offlineIcon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ContentValues values=new ContentValues();
				values.put(Client.Album.SHOULD_SYNC, Boolean.TRUE);
				context.getContentResolver().update(Uri.parse(entryUri), values, null, null);
			}
		});
		return newView;
	}

}
