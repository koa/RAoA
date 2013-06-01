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
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ShareActionProvider;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.album.AlbumOverviewActivity;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.ComplexCursorAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.TextViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoOverviewActivity extends Activity {
	private static class EntryValues {
		Collection<String> keywords = new HashSet<String>();
		Uri thumbnailUri;
	}

	private static interface KeywordsHandler {
		void handleKeywords(final Collection<String> keywords);
	}

	private enum UiMode {
		NAVIGATION, SELECTION
	}

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	/**
	 * 
	 */
	private static final String MODE_KEY = PhotoOverviewActivity.class.getName() + "-mode";

	/**
	 * 
	 */
	private static final String SELECTION_KEY = PhotoOverviewActivity.class.getName() + "-selection";

	private Uri albumEntriesUri;
	private String albumTitle = null;
	private int currentItemIndex;
	private UiMode currentMode = UiMode.NAVIGATION;

	private ComplexCursorAdapter cursorAdapter;

	private GridView gridview;

	private List<String> knownKeywords;

	private int lastLongClickposition = -1;

	private final Map<String, EntryValues> selectedEntries = new HashMap<String, EntryValues>();

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		switch (currentMode) {
		case SELECTION:
			getMenuInflater().inflate(R.menu.photo_overview_selection_menu, menu);

			final ShareActionProvider shareActionProvider = (ShareActionProvider) menu.findItem(R.id.photo_overview_menu_share).getActionProvider();
			final Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
			shareIntent.setType("image/jpeg");
			shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, makeCurrentSelectedUris());
			shareActionProvider.setShareIntent(shareIntent);

			final MenuItem addTagsMenu = menu.findItem(R.id.photo_overview_menu_add_tag_menu);
			final SubMenu tagsSubmenu = addTagsMenu.getSubMenu();
			tagsSubmenu.removeGroup(R.id.photo_overview_menu_existing_tag);
			final Map<String, Integer> selectedKeywordCounts = new HashMap<String, Integer>();
			for (final EntryValues entryValues : selectedEntries.values()) {
				for (final String keyword : entryValues.keywords) {
					final Integer existingKeyword = selectedKeywordCounts.get(keyword);
					if (existingKeyword != null) {
						selectedKeywordCounts.put(keyword, Integer.valueOf(existingKeyword.intValue() + 1));
					} else {
						selectedKeywordCounts.put(keyword, Integer.valueOf(1));
					}
					if (!knownKeywords.contains(keyword)) {
						knownKeywords.add(keyword);
					}
				}
			}
			for (final String keyword : knownKeywords) {
				final Integer count = selectedKeywordCounts.get(keyword);
				final String keywordDisplay = count != null ? keyword + " (" + count + ")" : keyword;
				final MenuItem item = tagsSubmenu.add(R.id.photo_overview_menu_existing_tag, Menu.NONE, Menu.NONE, keywordDisplay);
				item.setOnMenuItemClickListener(new OnMenuItemClickListener() {

					@Override
					public boolean onMenuItemClick(final MenuItem item) {
						setTagToSelectedEntries(keyword);
						return true;
					}
				});
			}

			final MenuItem removeTagsMenu = menu.findItem(R.id.photo_overview_menu_remove_tag_menu);
			final SubMenu removeTagsSubmenu = removeTagsMenu.getSubMenu();
			removeTagsSubmenu.clear();
			final ArrayList<String> keywordsByCount = orderByCount(selectedKeywordCounts);
			for (final String keyword : keywordsByCount) {
				final String keywordDisplay = keyword + " (" + selectedKeywordCounts.get(keyword) + ")";
				final MenuItem removeTagItem = removeTagsSubmenu.add(keywordDisplay);
				removeTagItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(final MenuItem item) {
						removeTagFromSelectedEntries(keyword);
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
		getActionBar().setDisplayHomeAsUpEnabled(true);
		// get album id out of intent
		final Bundle bundle = getIntent().getExtras();
		albumEntriesUri = Uri.parse(bundle.getString("album_entries_uri"));
		final Uri albumUri = Uri.parse(bundle.getString("album_uri"));
		Uri.parse(bundle.getString("album_uri"));
		setContentView(R.layout.photo_overview);

		if (savedInstanceState != null) {
			final String[] savedSelection = savedInstanceState.getStringArray(SELECTION_KEY);
			if (savedSelection != null) {
				for (final String selectedEntry : savedSelection) {
					selectedEntries.put(selectedEntry, new EntryValues());
				}
			}
		}

		final ComplexCursorAdapter adapter = new ComplexCursorAdapter(this, R.layout.photo_overview_item, makeHandlers(), new String[] { Client.AlbumEntry.ENTRY_URI,
																																																																		Client.AlbumEntry.META_KEYWORDS,
																																																																		Client.AlbumEntry.THUMBNAIL });
		getLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {

			@Override
			public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
				return new CursorLoader(PhotoOverviewActivity.this, albumEntriesUri, adapter.requiredFields(), null, null, null);
			}

			@Override
			public void onLoaderReset(final Loader<Cursor> loader) {
				adapter.swapCursor(null);
			}

			@Override
			public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
				final Collection<String> oldSelectedEntries = new HashSet<String>(selectedEntries.keySet());
				selectedEntries.clear();
				try {
					if (data == null || !data.moveToFirst()) {
						return;
					}
					final int entryColumn = data.getColumnIndex(Client.AlbumEntry.ENTRY_URI);
					final int keywordsColumn = data.getColumnIndex(Client.AlbumEntry.META_KEYWORDS);
					final int thumbnailColumn = data.getColumnIndex(Client.AlbumEntry.THUMBNAIL);
					do {
						final String uri = data.getString(entryColumn);
						if (oldSelectedEntries.contains(uri)) {
							selectedEntries.put(uri, makeEntry(data.getString(keywordsColumn), data.getString(thumbnailColumn)));
						}
					} while (data.moveToNext());
				} finally {
					adapter.swapCursor(data);
					invalidateOptionsMenu();
				}
			}
		});

		// Create an empty adapter we will use to display the loaded data.
		cursorAdapter = adapter;

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
		knownKeywords = getKnownKeywords();

		final UiMode mode = savedInstanceState == null ? UiMode.NAVIGATION : UiMode.valueOf(savedInstanceState.getString(MODE_KEY, UiMode.NAVIGATION.name()));
		switch (mode) {
		case NAVIGATION:
			activateNavigationMode();
			break;
		case SELECTION:
			activateSelectionMode();
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putCharSequence(MODE_KEY, currentMode.name());
		outState.putStringArray(SELECTION_KEY, selectedEntries.keySet().toArray(new String[0]));
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
		currentMode = UiMode.SELECTION;
		invalidateOptionsMenu();
	}

	private void addEntryToSelection(final Pair<String, EntryValues> pair) {
		selectedEntries.put(pair.first, pair.second);
		invalidateOptionsMenu();
	}

	private List<String> getKnownKeywords() {
		final Cursor cursor = getContentResolver().query(Client.KEYWORDS_URI, new String[] { Client.KeywordEntry.KEYWORD, Client.KeywordEntry.COUNT }, null, null, null);
		final List<String> keywordsFromCursor = readOrderedKeywordsFromCursor(cursor);
		return keywordsFromCursor;
	}

	private boolean longClick(final int position) {
		if (lastLongClickposition >= 0) {
			final int lower = Math.min(position, lastLongClickposition);
			final int upper = Math.max(position, lastLongClickposition);
			for (int i = lower; i <= upper; i++) {
				addEntryToSelection(readCurrentEntry(i));
			}
			lastLongClickposition = -1;
		} else {
			lastLongClickposition = position;
			addEntryToSelection(readCurrentEntry(position));
		}
		if (currentMode != UiMode.SELECTION) {
			activateSelectionMode();
		}
		redraw();
		return true;
	}

	/**
	 * @return
	 */
	private ArrayList<Uri> makeCurrentSelectedUris() {
		final ArrayList<Uri> ret = new ArrayList<Uri>();
		for (final EntryValues value : selectedEntries.values()) {
			ret.add(value.thumbnailUri);
		}
		return ret;
	}

	private EntryValues makeEntry(final String keywordValue, final String uriString) {
		final EntryValues values = new EntryValues();
		final Collection<String> keywords = Client.AlbumEntry.decodeKeywords(keywordValue);
		values.keywords = keywords;
		values.thumbnailUri = Uri.parse(uriString);
		return values;
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
				if (selectedEntries.containsKey(entryUri)) {
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

	private ArrayList<String> orderByCount(final Map<String, Integer> countOrder) {
		final ArrayList<String> keyWords = new ArrayList<String>(countOrder.keySet());
		Collections.sort(keyWords, new Comparator<String>() {
			@Override
			public int compare(final String lhs, final String rhs) {
				return -countOrder.get(lhs).compareTo(countOrder.get(rhs));
			}
		});
		return keyWords;
	}

	private Pair<String, EntryValues> readCurrentEntry(final int position) {
		final Object[] additionalValues = cursorAdapter.getAdditionalValues(position);
		final String uri = (String) additionalValues[0];
		final String keywordValue = (String) additionalValues[1];
		final String uriString = (String) additionalValues[2];
		return new Pair<String, EntryValues>(uri, makeEntry(keywordValue, uriString));
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
		return orderByCount(countOrder);
	}

	private void redraw() {
		gridview.requestLayout();
		gridview.invalidateViews();
	}

	private void removeTagFromSelectedEntries(final String keyword) {
		updateSelectedEntries(new KeywordsHandler() {
			@Override
			public void handleKeywords(final Collection<String> keywords) {
				keywords.remove(keyword);
			}
		});
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
		updateSelectedEntries(new KeywordsHandler() {
			@Override
			public void handleKeywords(final Collection<String> keywords) {
				keywords.add(tagValue);
			}
		});
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
		final Pair<String, EntryValues> currentEntry = readCurrentEntry(position);
		if (selectedEntries.remove(currentEntry.first) == null) {
			selectedEntries.put(currentEntry.first, currentEntry.second);
		}
		redraw();
		invalidateOptionsMenu();
	}

	private void updateSelectedEntries(final KeywordsHandler handler) {
		for (final String entryUri : selectedEntries.keySet()) {
			final ContentResolver resolver = getContentResolver();
			final Uri uri = Uri.parse(entryUri);
			final Cursor queryCursor = resolver.query(uri, new String[] { Client.AlbumEntry.META_KEYWORDS }, null, null, null);
			try {
				if (!queryCursor.moveToFirst()) {
					continue;
				}
				final Collection<String> keywords = new HashSet<String>(Client.AlbumEntry.decodeKeywords(queryCursor.getString(0)));
				handler.handleKeywords(keywords);
				final ContentValues values = new ContentValues();
				values.put(Client.AlbumEntry.META_KEYWORDS, Client.AlbumEntry.encodeKeywords(keywords));
				resolver.update(uri, values, null, null);
			} finally {
				queryCursor.close();
			}
		}
		invalidateOptionsMenu();
	}
}
