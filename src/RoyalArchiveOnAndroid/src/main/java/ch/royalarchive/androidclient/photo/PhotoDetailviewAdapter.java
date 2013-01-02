package ch.royalarchive.androidclient.photo;

import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.OverviewBinder;
import ch.royalarchive.androidclient.R;
import android.content.Context;
import android.widget.SimpleCursorAdapter;

public class PhotoDetailviewAdapter extends SimpleCursorAdapter {

	private static final String[] FROM = new String[] { 
		Client.AlbumEntry.THUMBNAIL };

	private static final int[] TO = new int[] {
		R.id.photo_detail_item_image };

	public PhotoDetailviewAdapter(Context context, int layout) {
		super(context, layout, null, FROM, TO, 0);
		
		// set photo overview view binder
		setViewBinder(new OverviewBinder(true));
	}


}
