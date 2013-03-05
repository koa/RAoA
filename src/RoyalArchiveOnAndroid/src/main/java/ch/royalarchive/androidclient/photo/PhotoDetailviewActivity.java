package ch.royalarchive.androidclient.photo;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Gallery;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;

public class PhotoDetailviewActivity extends Activity implements LoaderCallbacks<Cursor> {
	private static String TAG = PhotoDetailviewActivity.class.getSimpleName();
	
	private static final String ACTUAL_POS = "actPos";
	private static final String ALBUM_ID = "album_id";

	private int albumId;
	private int actPos;

	private PhotoDetailviewAdapter cursorAdapter;
	private Gallery photoGallery;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.photo_detailview);

		// get album id and photo id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt(ALBUM_ID);
		actPos = bundle.getInt(ACTUAL_POS);

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = new PhotoDetailviewAdapter(this, R.layout.photo_detailview_item);
		photoGallery = (Gallery) findViewById(R.id.photo_detail_gallery);
		photoGallery.setAdapter(cursorAdapter);
		
		Log.d(TAG, "onCreate: actual position: " + actPos);

		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "onResume: actual position: " + actPos);
		Log.d(TAG, "selected item pos before: " + photoGallery.getSelectedItemPosition() +":" + photoGallery.isSelected());
		photoGallery.setSelection(actPos);
		Log.d(TAG, "selected item pos after: " + photoGallery.getSelectedItemPosition() +":" + photoGallery.isSelected());
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		Log.d(TAG, "onCreateLoader: actual position: " + actPos);
		return new CursorLoader(this, Client.makeAlbumEntriesUri(albumId), null, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		cursorAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		cursorAdapter.swapCursor(null);
	}

}
