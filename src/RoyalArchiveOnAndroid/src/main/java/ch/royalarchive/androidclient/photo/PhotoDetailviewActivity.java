package ch.royalarchive.androidclient.photo;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Gallery;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;

public class PhotoDetailviewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private int albumId;

	private PhotoDetailviewAdapter cursorAdapter;
	private Gallery photoGallery;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.photo_detailview);

		// get album id and photo id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt("album_id");

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = new PhotoDetailviewAdapter(this, R.layout.photo_detailview_item);

		photoGallery = (Gallery) findViewById(R.id.photo_detail_gallery);
		photoGallery.setAdapter(cursorAdapter);
		
		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, Client.makeAlbumUri(albumId), null, null, null, null);
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
