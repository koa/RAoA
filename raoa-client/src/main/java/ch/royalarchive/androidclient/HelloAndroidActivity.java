package ch.royalarchive.androidclient;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.album.AlbumOverviewActivity;

public class HelloAndroidActivity extends ListActivity {

	/**
	 * Mein Test CursorAdapter für die Provider Test ListView.
	 * 
	 * @author noelle
	 * 
	 */
	private class ListAdapter extends CursorAdapter {

		public ListAdapter(final Context context, final Cursor c) {
			super(context, c);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor) {
			final TextView tvArchive = (TextView) view.findViewById(R.id.item_archive);
			tvArchive.setText(cursor.getString(cursor.getColumnIndexOrThrow(Client.Album.ARCHIVE_NAME)));

			final TextView tv = (TextView) view.findViewById(R.id.item_name);
			tv.setText(cursor.getString(cursor.getColumnIndexOrThrow(Client.Album.NAME)));

			final TextView tvCount = (TextView) view.findViewById(R.id.item_count);
			tvCount.setText(cursor.getString(cursor.getColumnIndexOrThrow(Client.Album.ENTRY_COUNT)));
		}

		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
			final LayoutInflater inflater = LayoutInflater.from(context);
			final View v = inflater.inflate(R.layout.list_item, parent, false);
			bindView(v, context, cursor);
			return v;
		}

	}

	private static String TAG = "RoyalArchiveOnAndroid";

	private Button overview;

	/**
	 * Called when the activity is first created.
	 * 
	 * @param savedInstanceState
	 *          If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in
	 *          onSaveInstanceState(Bundle). <b>Note: Otherwise it is null.</b>
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate");
		setContentView(R.layout.main);

		overview = (Button) findViewById(R.id.overview);
		overview.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				final Intent intent = new Intent(HelloAndroidActivity.this, AlbumOverviewActivity.class);
				startActivity(intent);
			}
		});

		final Button providertest = (Button) findViewById(R.id.providertest);
		providertest.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(final View v) {
				Log.i(TAG, "gedrückt!");
				testProvider();
			}
		});

		final Button clearlist = (Button) findViewById(R.id.clear_list);
		clearlist.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(final View v) {
				Log.i(TAG, "Liste löschen gedrückt!");
				clearList();
			}
		});
	}

	private void clearList() {
		final ListView lv = (ListView) findViewById(android.R.id.list);
		lv.setAdapter(null);
	}

	/**
	 * Mein Providertest. Listet alle Albennamen als Liste von TextViews auf.
	 */
	private void testProvider() {
		final ContentResolver resolver = getContentResolver();
		final Cursor albumCursor = resolver.query(Client.ALBUM_URI, null, null, null, null);

		final ListView lv = (ListView) findViewById(android.R.id.list);
		lv.setAdapter(new ListAdapter(this, albumCursor));

	}
}
