package ch.bergturbenthal.raoa.client.photo;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.CurserPagerAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoDetailViewActivity extends Activity {

	private static final String ACTUAL_POS = "actPos";

	private static final String ALBUM_ID = "album_uri";

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private static final String[] PROJECTION = new String[] { Client.AlbumEntry.THUMBNAIL };
	private int actPos;

	private CurserPagerAdapter adapter;
	private Uri albumUri;

	private PhotoDetailContainer detailContainer;

	private ViewPager pager;

	@Override
	public void onBackPressed() {
		final Intent output = new Intent();
		output.putExtra(CURR_ITEM_INDEX, ((CurserPagerAdapter) pager.getAdapter()).getCurrentPosition());
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
		adapter = CurserPagerAdapter.registerLoaderManager(getLoaderManager(), this, albumUri, R.layout.photo_detailview_item, makeHandlers());
		adapter.setCursorLoadedHandler(new Runnable() {

			@Override
			public void run() {
				pager.setCurrentItem(actPos, false);
			}
		});
		// adapter = new PhotoDetailviewAdapter(this);

		// View pager configuration
		pager.setAdapter(adapter);

		// Preload two pages
		pager.setOffscreenPageLimit(3);

		// Add a little space between pages
		pager.setPageMargin(15);

		// If hardware acceleration is enabled, you should also remove
		// clipping on the pager for its children.
		pager.setClipChildren(false);

		if (savedInstanceState != null) {
			actPos = savedInstanceState.getInt(ACTUAL_POS);
		}
	}

	@Override
	public void onLowMemory() {
		decrementOffscreenPageLimit();
		super.onLowMemory();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((CurserPagerAdapter) pager.getAdapter()).getCurrentPosition());
	}

	/**
	 * 
	 */
	private void decrementOffscreenPageLimit() {
		final int offscreenPageLimit = pager.getOffscreenPageLimit();
		if (offscreenPageLimit > 1) {
			pager.setOffscreenPageLimit(offscreenPageLimit - 1);
		}
	}

	private ArrayList<ViewHandler<? extends View>> makeHandlers() {
		final ArrayList<ViewHandler<? extends View>> ret = new ArrayList<ViewHandler<? extends View>>();
		ret.add(new PhotoViewHandler(R.id.photos_item_image, Client.Album.THUMBNAIL, PhotoViewHandler.FULLSCREEN_CALCULATOR));
		return ret;
	}

	private void setFullscreen(final boolean fullscreen) {
		getWindow().setFlags(fullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

}
