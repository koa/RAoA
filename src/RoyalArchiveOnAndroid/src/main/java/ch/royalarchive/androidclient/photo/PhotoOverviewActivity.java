package ch.royalarchive.androidclient.photo;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
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

public class PhotoOverviewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private SimpleCursorAdapter cursorAdapter;
	private int albumId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// get album id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt("album_id");
		setContentView(R.layout.photo_overview);

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = new PhotoOverviewAdapter(this, R.layout.photo_overview_item);

		GridView gridview = (GridView) findViewById(R.id.photo_overview);
		gridview.setAdapter(cursorAdapter);

		// Handle click on photo
		gridview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				Toast.makeText(PhotoOverviewActivity.this, "pos: " + position + " id: " + id, Toast.LENGTH_SHORT).show();
				// Intent intent = new Intent(PhotoOverviewActivity.this, PhotoOverviewActivity.class);
				// intent.putExtra("album_id", (Integer) (v.getTag()));
				// startActivity(intent);
			}
		});

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
