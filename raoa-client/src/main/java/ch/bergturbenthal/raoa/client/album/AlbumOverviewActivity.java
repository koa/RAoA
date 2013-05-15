package ch.bergturbenthal.raoa.client.album;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.photo.PhotoOverviewActivity;
import ch.bergturbenthal.raoa.provider.Client;

public class AlbumOverviewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static String TAG = AlbumOverviewActivity.class.getSimpleName();
	private SimpleCursorAdapter cursorAdapter;

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		return new CursorLoader(this, Client.ALBUM_URI, null, null, null, null);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.album_overview_menu, menu);
		return true;
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		cursorAdapter.swapCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		cursorAdapter.swapCursor(data);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.createAlbumMenuItem:
			startActivity(new Intent(this, CreateAlbumActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.album_overview);

		// Create the album overview adapter we will use to display the loaded data
		cursorAdapter = new AlbumOverviewAdapter(this, R.layout.album_overview_item);

		final GridView gridview = (GridView) findViewById(R.id.album_overview);
		gridview.setAdapter(cursorAdapter);

		// Handle clicks on album image
		gridview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View v, final int position, final long id) {
				final Intent intent = new Intent(AlbumOverviewActivity.this, PhotoOverviewActivity.class);
				intent.putExtra("album_uri", (String) (v.getTag()));
				startActivity(intent);
			}
		});

		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);
	}

}
