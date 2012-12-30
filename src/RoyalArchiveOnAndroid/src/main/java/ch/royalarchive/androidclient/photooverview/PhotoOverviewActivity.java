package ch.royalarchive.androidclient.photooverview;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;

public class PhotoOverviewActivity extends Activity implements LoaderCallbacks<Cursor>{
	
	private SimpleCursorAdapter cursorAdapter;
	private int albumId;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// get album id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt("album_id");
		setContentView(R.layout.photo_overview);
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = new SimpleCursorAdapter(this, R.layout.photo_overview_item, null, 
				new String[] { 
					Client.AlbumEntry.THUMBNAIL }, 
				new int[] { 
					R.id.photo_item_image }, 0);
		cursorAdapter.setViewBinder(new PhotoOverviewViewBinder());

		GridView gridview = (GridView) findViewById(R.id.photo_overview);
		gridview.setAdapter(cursorAdapter);

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
