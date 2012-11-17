package ch.royalarchive.androidclient;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.image.provider.Client;

public class AlbumOverviewActivity extends Activity implements
		LoaderCallbacks<Cursor> {
	private static String TAG = AlbumOverviewActivity.class.getSimpleName();

	private SimpleCursorAdapter cursorAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.album_overview);

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = new SimpleCursorAdapter(this,
				R.layout.album_overview_item, null, new String[] {
						Client.Album.NAME, Client.Album.ENTRY_COUNT },
				new int[] { R.id.album_item_name, R.id.album_item_size }, 0);
		GridView gridview = (GridView) findViewById(R.id.album_overview);
		gridview.setAdapter(cursorAdapter);

		/*
		 * gridview.setOnItemClickListener(new OnItemClickListener() { public
		 * void onItemClick(AdapterView<?> parent, View v, int position, long
		 * id) { Toast.makeText(AlbumOverviewActivity.this, "" + position,
		 * Toast.LENGTH_SHORT).show(); } });
		 */

		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);

	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, Client.ALBUM_URI, null, null, null, null);
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
