package ch.royalarchive.androidclient.album;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;
import ch.royalarchive.androidclient.photo.PhotoOverviewActivity;

public class AlbumOverviewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static String TAG = AlbumOverviewActivity.class.getSimpleName();
	private SimpleCursorAdapter cursorAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.album_overview);

		// Create the album overview adapter we will use to display the loaded data
		cursorAdapter = new AlbumOverviewAdapter(this, R.layout.album_overview_item);
		
		GridView gridview = (GridView) findViewById(R.id.album_overview);
		gridview.setAdapter(cursorAdapter);

		// Handle clicks on album image
		gridview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				Intent intent = new Intent(AlbumOverviewActivity.this, PhotoOverviewActivity.class);
				intent.putExtra("album_id", (Integer) (v.getTag()));
				startActivity(intent);
			}
		});
		
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
