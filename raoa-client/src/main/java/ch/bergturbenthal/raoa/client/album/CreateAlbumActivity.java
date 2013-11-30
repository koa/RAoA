package ch.bergturbenthal.raoa.client.album;

import java.util.Collection;
import java.util.TreeSet;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.SimpleCursorAdapter;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.provider.Client;

public class CreateAlbumActivity extends Activity {

	private void initServerSpinner(final AdapterView<Adapter> selectServerSpinner) {
		final CursorAdapter adapter = new SimpleCursorAdapter(getApplicationContext(),
																													android.R.layout.simple_spinner_item,
																													null,
																													new String[] { Client.ServerEntry.SERVER_NAME },
																													new int[] { android.R.id.text1 },
																													0);
		selectServerSpinner.setAdapter(adapter);

		getLoaderManager().initLoader(R.id.selectServerSpinner, null, new LoaderCallbacks<Cursor>() {

			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(getApplicationContext(), Client.SERVER_URI, null, null, null, null);
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.swapCursor(null);

			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				adapter.swapCursor(data);
			}
		});
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_album);
		initServerSpinner((AdapterView<Adapter>) findViewById(R.id.selectServerSpinner));
		final AdapterView<Adapter> selectFolderSpinner = (AdapterView) findViewById(R.id.selectFolderSpinner);
		final CursorAdapter adapter = new SimpleCursorAdapter(getApplicationContext(),
																													android.R.layout.simple_spinner_item,
																													null,
																													new String[] { "folder" },
																													new int[] { android.R.id.text1 },
																													0);
		selectFolderSpinner.setAdapter(adapter);
		getLoaderManager().initLoader(R.id.selectFolderSpinner, null, new LoaderCallbacks<Cursor>() {

			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(getApplicationContext(), Client.ALBUM_URI, new String[] { Client.Album.NAME }, null, null, null) {

					@Override
					public Cursor loadInBackground() {
						final Cursor data = super.loadInBackground();
						final Collection<String> existingFolders = new TreeSet<String>();
						while (data.moveToNext()) {
							final String albumDir = data.getString(0);
							final int lastSlash = albumDir.lastIndexOf('/');
							if (lastSlash <= 1) {
								continue;
							}
							final String folderPart = albumDir.substring(0, lastSlash);
							existingFolders.add(folderPart);
						}
						existingFolders.add("");
						final MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "folder" });
						int i = 0;
						for (final String folderName : existingFolders) {
							cursor.addRow(new Object[] { Integer.valueOf(i++), folderName });
						}
						return cursor;
					}
				};
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.swapCursor(null);

			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				adapter.swapCursor(data);
			}
		});
	}
}
