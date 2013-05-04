package ch.bergturbenthal.raoa.client.photo;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Window;
import android.view.WindowManager;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoDetailViewActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static final String ACTUAL_POS = "actPos";

	private static final String ALBUM_ID = "album_uri";

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private static final String[] PROJECTION = new String[] { Client.AlbumEntry.THUMBNAIL };
	private int actPos;

	private PhotoDetailviewAdapter adapter;
	private Uri albumUri;

	private PhotoDetailContainer detailContainer;

	private ViewPager pager;

	@Override
	public void onBackPressed() {
		final Intent output = new Intent();
		output.putExtra(CURR_ITEM_INDEX, ((PhotoDetailviewAdapter) pager.getAdapter()).getCurrentPosition());
		setResult(RESULT_OK, output);
		super.onBackPressed();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setFullscreen(true);

		setContentView(R.layout.photo_detailview);
		detailContainer = (PhotoDetailContainer) findViewById(R.id.photo_detailview_container);

		pager = detailContainer.getViewPager();

		// get album id and photo id out of intent
		final Bundle bundle = getIntent().getExtras();
		albumUri = Uri.parse(bundle.getString(ALBUM_ID));
		actPos = bundle.getInt(ACTUAL_POS);
		adapter = new PhotoDetailviewAdapter(this, null);

		// View pager configuration
		pager.setAdapter(adapter);

		// Preload two pages
		pager.setOffscreenPageLimit(1);

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
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		return new CursorLoader(this, albumUri, PROJECTION, null, null, null);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		adapter.swapCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		adapter.swapCursor(data);

		// set specific item
		pager.setCurrentItem(actPos, false);
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((PhotoDetailviewAdapter) pager.getAdapter()).getCurrentPosition());
	}

	private void setFullscreen(final boolean fullscreen) {
		getWindow().setFlags(fullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

}
