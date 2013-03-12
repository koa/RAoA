package ch.royalarchive.androidclient.photo;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;

public class PhotoDetailViewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static final String[] PROJECTION = new String[] { Client.AlbumEntry.THUMBNAIL };

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private PhotoDetailContainer detailContainer;

	private static final String ACTUAL_POS = "actPos";
	private static final String ALBUM_ID = "album_id";

	private int albumId;
	private int actPos;

	private PhotoDetailviewAdapter adapter;

	private ViewPager pager;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.photo_detailview);
		detailContainer = (PhotoDetailContainer) findViewById(R.id.photo_detailview_container);

		pager = detailContainer.getViewPager();

		// get album id and photo id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt(ALBUM_ID);
		actPos = bundle.getInt(ACTUAL_POS);
		adapter = new PhotoDetailviewAdapter(this, null);

		// View pager configuration
		pager.setAdapter(adapter);

		// Preload two pages
		pager.setOffscreenPageLimit(2);

		// Add a little space between pages
		pager.setPageMargin(15);

		// If hardware acceleration is enabled, you should also remove
		// clipping on the pager for its children.
		pager.setClipChildren(false);
		
		if (savedInstanceState != null) {
      actPos = savedInstanceState.getInt(ACTUAL_POS);
		}

		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((PhotoDetailviewAdapter) pager.getAdapter()).getCurrentPosition());
	}

	@Override
	public void onBackPressed() {
		Intent output = new Intent();
		output.putExtra(CURR_ITEM_INDEX, ((PhotoDetailviewAdapter) pager.getAdapter()).getCurrentPosition());
		setResult(RESULT_OK, output);
		super.onBackPressed();
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, Client.makeAlbumEntriesUri(albumId), PROJECTION, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		adapter.swapCursor(data);

		// set specific item
		pager.setCurrentItem(actPos, false);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		adapter.swapCursor(null);
	}

}
