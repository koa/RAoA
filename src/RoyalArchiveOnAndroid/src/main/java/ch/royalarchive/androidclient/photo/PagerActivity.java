package ch.royalarchive.androidclient.photo;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;
import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.OverviewBinder;
import ch.royalarchive.androidclient.R;

public class PagerActivity extends FragmentActivity implements LoaderCallbacks<Cursor> {
	private static final String[] PROJECTION = new String[] { 
		Client.AlbumEntry.THUMBNAIL };
	
	private PagerContainer mContainer;

	private static final String ACTUAL_POS = "actPos";
	private static final String ALBUM_ID = "album_id";

	private int albumId;
	private int actPos;

	private MyPagerAdapter adapter;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    setContentView(R.layout.main_view_pager);

		// get album id and photo id out of intent
		Bundle bundle = getIntent().getExtras();
		albumId = bundle.getInt(ALBUM_ID);
		actPos = bundle.getInt(ACTUAL_POS);
		
    mContainer = (PagerContainer) findViewById(R.id.pager_container);

    ViewPager pager = mContainer.getViewPager();
    Cursor cursor = getContentResolver().query(Client.makeAlbumEntriesUri(albumId), PROJECTION, null, null, null);
    adapter = new MyPagerAdapter(cursor);
    pager.setAdapter(adapter);
    //Necessary or the pager will only have one extra page to show
    // make this at least however many pages you can see
    pager.setOffscreenPageLimit(2);
    //A little space between pages
    pager.setPageMargin(15);

    //If hardware acceleration is enabled, you should also remove
    // clipping on the pager for its children.
    pager.setClipChildren(false);
    
		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		getLoaderManager().initLoader(0, null, this);
	}

	// Nothing special about this adapter, just throwing up colored views for demo
	private class MyPagerAdapter extends PagerAdapter {		
		private Cursor cursor;
		private final ViewBinder viewBinder;
		
		public MyPagerAdapter(Cursor cursor) {
			this.cursor = cursor;
			
			// set photo overview view binder
			viewBinder = new OverviewBinder(true);
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			ImageView view = new ImageView(PagerActivity.this);
			cursor.moveToPosition(position);
			viewBinder.setViewValue(view, cursor, 0);
			container.addView(view);
			return view;
		}

		@Override
		public void destroyItem(View container, int position, Object object) {
			((ViewPager)container).removeView((View) object);
		}

		@Override
		public int getCount() {
			if (cursor == null)
				return 0;
			else
				return cursor.getCount();
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return (view == object);
		}

		public void swapCursor(Cursor c) {
			if (cursor == c)
				return;

			this.cursor = c;
			notifyDataSetChanged();
		}

		public Cursor getCursor() {
			return cursor;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, Client.makeAlbumEntriesUri(albumId), PROJECTION, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		adapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		adapter.swapCursor(null);
	}

}
