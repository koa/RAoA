package ch.bergturbenthal.raoa.client.photo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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

	private Uri albumEntriesUri;

	private String albumTitle = null;
	private int currentItemIndex;
	private UiMode currentMode = UiMode.NAVIGATION;
	private ComplexCursorAdapter cursorAdapter;

	private GridView gridview;

	private int lastLongClickposition = -1;

	private final Collection<String> selectedEntries = new HashSet<String>();

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		switch (currentMode) {
		case SELECTION:
			getMenuInflater().inflate(R.menu.photo_overview_selection_menu, menu);

			final MenuItem tagsMenu = menu.findItem(R.id.photo_overview_menu_tag_list_menu);
			final SubMenu tagsSubmenu = tagsMenu.getSubMenu();
			tagsSubmenu.removeGroup(R.id.photo_overview_menu_existing_tag);
			final Cursor cursor = getContentResolver().query(Client.KEYWORDS_URI, new String[] { Client.KeywordEntry.KEYWORD, Client.KeywordEntry.COUNT }, null, null, null);
			final List<String> keywordsFromCursor = readOrderedKeywordsFromCursor(cursor);
			for (final String keyword : keywordsFromCursor) {
				final MenuItem item = tagsSubmenu.add(R.id.photo_overview_menu_existing_tag, Menu.NONE, Menu.NONE, keyword);
				item.setOnMenuItemClickListener(new OnMenuItemClickListener() {

					@Override
					public boolean onMenuItemClick(final MenuItem item) {
						setTagToSelectedEntries(keyword);
						return true;
					}
				});
			}

			// final SubMenu subMenu = findItem.getSubMenu();
			// subMenu.clear();
			// subMenu.add("Hello Tag");
			break;
		case NAVIGATION:
			menu.clear();
			break;
		}

		return true;
	}

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
		case R.id.photo_overview_menu_add_new_tag:
			setNewTag();
			return true;
		case R.id.photo_overview_menu_close_selection:
			activateNavigationMode();
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
		albumEntriesUri = Uri.parse(bundle.getString("album_entries_uri"));
		final Uri albumUri = Uri.parse(bundle.getString("album_uri"));
		Uri.parse(bundle.getString("album_uri"));
		setContentView(R.layout.photo_overview);

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = ComplexCursorAdapter.registerLoaderManager(	getLoaderManager(),
																																this,
																																albumEntriesUri,
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

		final Cursor cursor = getContentResolver().query(albumUri, new String[] { Client.Album.TITLE }, null, null, null);
		if (cursor.moveToFirst()) {
			albumTitle = cursor.getString(0);
		}
		activateNavigationMode();
	}

	private void activateNavigationMode() {
		currentMode = UiMode.NAVIGATION;
		if (albumTitle != null) {
			getActionBar().setTitle(albumTitle);
		}
		selectedEntries.clear();
		lastLongClickposition = -1;
		redraw();
		invalidateOptionsMenu();
	}

	private void activateSelectionMode() {
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item);
		getLoaderManager().initLoader(1, null, new LoaderCallbacks<Cursor>() {

			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(PhotoOverviewActivity.this,
																Client.KEYWORDS_URI,
																new String[] { Client.KeywordEntry.KEYWORD, Client.KeywordEntry.COUNT },
																null,
																null,
																null);
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.clear();
			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				fillTagsFromCursor(adapter, data);
			}
		});
		currentMode = UiMode.SELECTION;
		invalidateOptionsMenu();
	}

	private void fillTagsFromCursor(final ArrayAdapter<String> adapter, final Cursor data) {
		adapter.clear();
		adapter.addAll(readOrderedKeywordsFromCursor(data));
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
		if (currentMode != UiMode.SELECTION) {
			activateSelectionMode();
		}
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
		intent.putExtra("album_uri", albumEntriesUri.toString());
		intent.putExtra("actPos", position);
		startActivityForResult(intent, 1);
	}

	private String readCurrentEntryUri(final int position) {
		return (String) cursorAdapter.getAdditionalValues(position)[0];
	}

	private List<String> readOrderedKeywordsFromCursor(final Cursor data) {
		if (data == null || !data.moveToFirst()) {
			return Collections.emptyList();
		}
		final Map<String, Integer> countOrder = new HashMap<String, Integer>();
		do {
			final String keyword = data.getString(0);
			final int count = data.getInt(1);
			countOrder.put(keyword, Integer.valueOf(count));
		} while (data.moveToNext());
		final ArrayList<String> keyWords = new ArrayList<String>(countOrder.keySet());
		Collections.sort(keyWords, new Comparator<String>() {
			@Override
			public int compare(final String lhs, final String rhs) {
				return -countOrder.get(lhs).compareTo(countOrder.get(rhs));
			}
		});
		return keyWords;
	}

	private void redraw() {
		gridview.requestLayout();
		gridview.invalidateViews();
	}

	/**
	 * 
	 */
	private void setNewTag() {
		final EditText newTagValue = new EditText(this);
		new AlertDialog.Builder(this).setTitle("Input new Tag").setView(newTagValue).setPositiveButton("Ok", new OnClickListener() {

			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				setTagToSelectedEntries(newTagValue.getText().toString());
			}
		}).setNegativeButton("Cancel", new OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
			}
		}).show();
	}

	private void setTagToSelectedEntries(final String tagValue) {
		for (final String entryUri : selectedEntries) {
			final ContentResolver resolver = getContentResolver();
			final Uri uri = Uri.parse(entryUri);
			final Cursor queryCursor = resolver.query(uri, new String[] { Client.AlbumEntry.META_KEYWORDS }, null, null, null);
			try {
				if (!queryCursor.moveToFirst()) {
					continue;
				}
				final Collection<String> keywords = new HashSet<String>(Client.AlbumEntry.decodeKeywords(queryCursor.getString(0)));
				keywords.add(tagValue);
				final ContentValues values = new ContentValues();
				values.put(Client.AlbumEntry.META_KEYWORDS, Client.AlbumEntry.encodeKeywords(keywords));
				resolver.update(uri, values, null, null);
			} finally {
				queryCursor.close();
			}
		}
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
