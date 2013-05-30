package ch.bergturbenthal.raoa.client.photo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.album.AlbumOverviewActivity;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.ComplexCursorAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.TextViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoOverviewActivity extends Activity {
	private enum UiMode {
		NAVIGATION, SELECTION
	}

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private Uri albumUri;

	private int currentItemIndex;
	private UiMode currentMode = UiMode.NAVIGATION;
	private ComplexCursorAdapter cursorAdapter;
	private GridView gridview;

	private int lastLongClickposition = -1;

	private final Collection<String> selectedEntries = new HashSet<String>();

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			final Intent upIntent = new Intent(this, AlbumOverviewActivity.class);
			upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(upIntent);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		final Bundle bundle = data.getExtras();
		currentItemIndex = bundle.getInt(CURR_ITEM_INDEX);
		gridview.setSelection(currentItemIndex);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		currentMode = UiMode.NAVIGATION;
		getActionBar().setDisplayHomeAsUpEnabled(true);
		// get album id out of intent
		final Bundle bundle = getIntent().getExtras();
		albumUri = Uri.parse(bundle.getString("album_uri"));
		setContentView(R.layout.photo_overview);

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = ComplexCursorAdapter.registerLoaderManager(	getLoaderManager(),
																																this,
																																albumUri,
																																R.layout.photo_overview_item,
																																makeHandlers(),
																																new String[] { Client.AlbumEntry.ENTRY_URI });

		gridview = (GridView) findViewById(R.id.photo_overview);
		gridview.setAdapter(cursorAdapter);

		// Handle click on photo
		gridview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View v, final int position, final long id) {
				shortClick(position);
			}

		});
		gridview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				return longClick(position);
			}
		});
		gridview.setWillNotCacheDrawing(false);
	}

	private boolean longClick(final int position) {
		if (lastLongClickposition >= 0) {
			final int lower = Math.min(position, lastLongClickposition);
			final int upper = Math.max(position, lastLongClickposition);
			for (int i = lower; i <= upper; i++) {
				selectedEntries.add(readCurrentEntryUri(i));
			}
			lastLongClickposition = -1;
		} else {
			lastLongClickposition = position;
		}
		final String entryUri = readCurrentEntryUri(position);
		selectedEntries.add(entryUri);
		currentMode = UiMode.SELECTION;
		redraw();
		return true;
	}

	/**
	 * @return
	 */
	private Collection<ViewHandler<? extends View>> makeHandlers() {
		final ArrayList<ViewHandler<? extends View>> ret = new ArrayList<ViewHandler<? extends View>>();
		ret.add(new PhotoViewHandler(R.id.photos_item_image, Client.AlbumEntry.THUMBNAIL, new PhotoViewHandler.DimensionCalculator(R.dimen.image_width)));
		ret.add(new TextViewHandler(R.id.photo_name, Client.AlbumEntry.NAME));
		ret.add(new AbstractViewHandler<View>(R.id.photos_overview_grid_item) {

			@Override
			public void bindView(final View view, final Context context, final Map<String, Object> values) {
				final String entryUri = (String) values.get(Client.AlbumEntry.ENTRY_URI);
				if (selectedEntries.contains(entryUri)) {
					view.setBackgroundResource(R.drawable.layout_border);
				} else {
					view.setBackgroundResource(R.drawable.layout_no_border);
				}
			}

			@Override
			public String[] usedFields() {
				return new String[] { Client.AlbumEntry.ENTRY_URI };
			}
		});
		return ret;
	}

	private void openDetailView(final int position) {
		final Intent intent = new Intent(PhotoOverviewActivity.this, PhotoDetailViewActivity.class);
		intent.putExtra("album_uri", albumUri.toString());
		intent.putExtra("actPos", position);
		startActivityForResult(intent, 1);
	}

	private String readCurrentEntryUri(final int position) {
		return (String) cursorAdapter.getAdditionalValues(position)[0];
	}

	private void redraw() {
		gridview.requestLayout();
		gridview.invalidateViews();
	}

	private void shortClick(final int position) {
		switch (currentMode) {
		case NAVIGATION:
			openDetailView(position);
			break;
		case SELECTION:
			toggleSelection(position);
			break;
		}
	}

	/**
	 * @param position
	 */
	private void toggleSelection(final int position) {
		final String uri = readCurrentEntryUri(position);
		if (!selectedEntries.remove(uri)) {
			selectedEntries.add(uri);
		}
		redraw();
	}
}
