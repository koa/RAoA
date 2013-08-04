package ch.bergturbenthal.raoa.client.photo;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ShareActionProvider;
import android.widget.ToggleButton;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.album.AlbumOverviewActivity;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.CursorPagerAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.client.util.KeywordUtil;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryType;

public class PhotoDetailViewActivity extends Activity {

	private static interface TagViewHandler {
		void setVisibleTags(final String[] tags);

		void showTags(final boolean visibility);
	}

	private static interface TagViewHandlerUpdater {
		void doUpdate(final TagViewHandler handler);
	}

	public static final String ACTUAL_POS = "actPos";

	public static final String ALBUM_URI = "album_uri";

	public static final String CURRENT_FILTER = "current_filter";
	protected static final int VISIBLE_KEYWORD_COUNT = 5;

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private static final String TAG_HEAT_MAP = "tagHeatMap";
	protected List<String> knownKeywords = Collections.emptyList();

	private int actPos;

	private CursorPagerAdapter adapter;
	private Uri albumUri;

	private PhotoDetailContainer detailContainer;

	private boolean isOverlayVisible = false;

	private ViewPager pager;

	private final Collection<WeakReference<TagViewHandler>> tagHandlers = new LinkedList<WeakReference<TagViewHandler>>();

	private final Map<String, Integer> tagHeatMap = new HashMap<String, Integer>();

	private boolean tagsVisible = false;

	private String[] visibleKeywords = new String[0];

	@Override
	public void onBackPressed() {
		final Intent output = new Intent();
		output.putExtra(CURR_ITEM_INDEX, ((CursorPagerAdapter) pager.getAdapter()).getCurrentPosition());
		setResult(RESULT_OK, output);
		super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final Object[] additionalValues = adapter.getAdditionalValues(actPos);
		if (additionalValues == null) {
			return false;
		}
		getMenuInflater().inflate(R.menu.photo_detail_menu, menu);

		final ShareActionProvider shareActionProvider = (ShareActionProvider) menu.findItem(R.id.photo_overview_menu_share).getActionProvider();
		final Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("image/jpeg");
		shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse((String) additionalValues[0]));
		shareActionProvider.setShareIntent(shareIntent);
		return true;
	}

	@Override
	public void onLowMemory() {
		decrementOffscreenPageLimit();
		super.onLowMemory();
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
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		final ActionBar actionBar = getActionBar();
		if (actionBar.isShowing()) {
			actionBar.hide();
		} else {
			actionBar.show();
		}
		return super.onTouchEvent(event);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		setFullscreen(true);

		setContentView(R.layout.photo_detailview);
		detailContainer = (PhotoDetailContainer) findViewById(R.id.photo_detailview_container);
		pager = detailContainer.getViewPager();

		// get album id and photo id out of intent
		final Bundle bundle = getIntent().getExtras();
		albumUri = Uri.parse(bundle.getString(ALBUM_URI));
		final String currentFilter = bundle.getString(CURRENT_FILTER);
		actPos = bundle.getInt(ACTUAL_POS);

		adapter = CursorPagerAdapter.registerLoaderManager(	getLoaderManager(),
																												this,
																												albumUri,
																												currentFilter,
																												null,
																												R.layout.photo_detailview_item,
																												makeHandlers(),
																												new String[] { Client.AlbumEntry.THUMBNAIL_ALIAS });
		adapter.setCursorLoadedHandler(new Runnable() {

			@Override
			public void run() {
				pager.setCurrentItem(actPos, false);
				invalidateOptionsMenu();
			}
		});
		adapter.setListView(detailContainer);
		adapter.setEmptyView(findViewById(R.id.photo_detailview_empty_view));
		detailContainer.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
			}

			@Override
			public void onPageSelected(final int position) {
				actPos = position;
				invalidateOptionsMenu();
			}

		});

		// View pager configuration
		pager.setAdapter(adapter);

		// Preload two pages
		pager.setOffscreenPageLimit(3);

		// Add a little space between pages
		pager.setPageMargin(15);

		// If hardware acceleration is enabled, you should also remove
		// clipping on the pager for its children.
		pager.setClipChildren(false);
		new AsyncTask<Void, Void, Void>() {
			private List<String> knownKeywords;
			private String[] visibleKeywords;

			@Override
			protected Void doInBackground(final Void... params) {
				knownKeywords = KeywordUtil.getKnownKeywords(getContentResolver());
				if (knownKeywords.size() > VISIBLE_KEYWORD_COUNT) {
					visibleKeywords = knownKeywords.subList(0, VISIBLE_KEYWORD_COUNT).toArray(new String[VISIBLE_KEYWORD_COUNT]);
				} else {
					visibleKeywords = knownKeywords.toArray(new String[knownKeywords.size()]);
				}
				return null;
			}

			@Override
			protected void onPostExecute(final Void result) {
				updateVisibleKeywords(visibleKeywords);
				PhotoDetailViewActivity.this.knownKeywords = knownKeywords;
			}
		}.execute();

		if (savedInstanceState != null) {
			actPos = savedInstanceState.getInt(ACTUAL_POS);
			tagHeatMap.clear();
			final Map<String, Integer> savedHeatMap = (Map<String, Integer>) savedInstanceState.getSerializable(TAG_HEAT_MAP);
			if (savedHeatMap != null) {
				tagHeatMap.putAll(savedHeatMap);
			}
		}
		setupActionBar();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((CursorPagerAdapter) pager.getAdapter()).getCurrentPosition());
		outState.putSerializable(TAG_HEAT_MAP, (Serializable) tagHeatMap);
	}

	protected final void reloadViewPager() {
		final int currentItem = pager.getCurrentItem();
		pager.setAdapter(adapter);
		pager.setCurrentItem(currentItem, false);
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
		ret.add(new PhotoViewHandler(R.id.photos_item_image, Client.AlbumEntry.THUMBNAIL_ALIAS, PhotoViewHandler.FULLSCREEN_CALCULATOR, 0));
		ret.add(makeTagButtonsViewHandler());
		ret.add(new AbstractViewHandler<View>(R.id.photo_view_play_video_button) {

			@Override
			public void bindView(final View view, final Context context, final Map<String, Object> values) {
				final boolean isVideo = values.get(Client.AlbumEntry.ENTRY_TYPE).equals(AlbumEntryType.VIDEO.name());
				if (isVideo) {
					view.setVisibility(View.VISIBLE);
					final String thumbnail = (String) values.get(Client.AlbumEntry.THUMBNAIL_ALIAS);
					view.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(final View v) {
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(thumbnail)));
						}
					});
				} else {
					view.setVisibility(View.INVISIBLE);
				}
			}

			@Override
			public String[] usedFields() {
				return new String[] { Client.AlbumEntry.ENTRY_TYPE, Client.AlbumEntry.THUMBNAIL_ALIAS };
			}
		});
		return ret;
	}

	private AbstractViewHandler<View> makeTagButtonsViewHandler() {
		return new AbstractViewHandler<View>(R.id.FrameLayout1) {

			@Override
			public void bindView(final View view, final Context context, final Map<String, Object> values) {
				final String entryUri = (String) values.get(Client.AlbumEntry.ENTRY_URI);

				final List<ToggleButton> tagToggleButtons = new ArrayList<ToggleButton>();
				final ViewGroup toggleButtonGroup = (ViewGroup) view.findViewById(R.id.photo_edit_toggle_button_container);
				for (int i = 0; i < toggleButtonGroup.getChildCount(); i++) {
					final View childAt = toggleButtonGroup.getChildAt(i);
					if (!(childAt instanceof ToggleButton)) {
						continue;
					}
					final ToggleButton toggleButton = (ToggleButton) childAt;
					toggleButton.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(final View v) {
							final boolean isChecked = ((ToggleButton) v).isChecked();
							final String keyWord = (String) v.getTag();
							if (keyWord == null) {
								return;
							}
							registerTagTouched(keyWord);
							updateKeyword(entryUri, keyWord, isChecked);
						}

					});
					tagToggleButtons.add(toggleButton);
				}
				final Collection<String> keywordList = new HashSet<String>(Client.AlbumEntry.decodeKeywords((String) values.get(Client.AlbumEntry.META_KEYWORDS)));
				final LinearLayout overlayLayout = (LinearLayout) view.findViewById(R.id.photo_edit_overlay_layout);

				final TagViewHandler updateHandler = new TagViewHandler() {

					@Override
					public void setVisibleTags(final String[] tags) {
						for (int i = 0; i < tagToggleButtons.size(); i++) {
							final ToggleButton button = tagToggleButtons.get(i);
							if (tags.length > i && tags[i] != null) {
								final String tag = tags[i];
								button.setVisibility(View.VISIBLE);
								button.setText(tag);
								button.setTextOff(tag);
								button.setTextOn(tag);
								button.setTag(tag);
								button.setChecked(keywordList.contains(tag));
							} else {
								button.setVisibility(View.INVISIBLE);
							}
							button.invalidate();
						}
						overlayLayout.invalidate();
					}

					@Override
					public void showTags(final boolean visibility) {
						overlayLayout.setVisibility(visibility ? View.VISIBLE : View.INVISIBLE);
						overlayLayout.invalidate();
					}
				};
				tagHandlers.add(new WeakReference<TagViewHandler>(updateHandler));
				overlayLayout.setTag(updateHandler);
				updateHandler.setVisibleTags(visibleKeywords);

				if (tagsVisible) {
					// overlayLayout.removeAllViewsInLayout();
					overlayLayout.setVisibility(View.VISIBLE);
				} else {
					overlayLayout.setVisibility(View.INVISIBLE);
				}
				final Button addTagButton = (Button) view.findViewById(R.id.photo_edit_add_tag_button);
				addTagButton.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(final View v) {
						final TreeSet<String> allKeywords = new TreeSet<String>(knownKeywords);
						allKeywords.addAll(tagHeatMap.keySet());

						final LayoutInflater inflater = getLayoutInflater();
						final View dialogContentLayout = inflater.inflate(R.layout.photo_detailview_input_tag, null);

						final AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView) dialogContentLayout.findViewById(R.id.photo_detailview_input_tag_view);

						autoCompleteTextView.setAdapter(new ArrayAdapter<String>(	PhotoDetailViewActivity.this,
																																			android.R.layout.simple_dropdown_item_1line,
																																			new ArrayList<String>(allKeywords)));
						final AlertDialog dialog = new AlertDialog.Builder(PhotoDetailViewActivity.this).setView(dialogContentLayout)
																																														.setPositiveButton(	android.R.string.ok,
																																																								new DialogInterface.OnClickListener() {

																																																									@Override
																																																									public void onClick(final DialogInterface dialog,
																																																																			final int which) {
																																																										placeNewTag(autoCompleteTextView.getText().toString());
																																																										dialog.cancel();
																																																									}

																																																								})
																																														.setNegativeButton(	android.R.string.cancel,
																																																								new DialogInterface.OnClickListener() {

																																																									@Override
																																																									public void onClick(final DialogInterface dialog,
																																																																			final int which) {
																																																										dialog.cancel();
																																																									}
																																																								})
																																														.create();
						dialog.show();
					}
				});

				view.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(final View v) {
						isOverlayVisible = !isOverlayVisible;
						toggleActionBar();
					}

					private void toggleActionBar() {
						final ActionBar actionBar = getActionBar();
						if (actionBar.isShowing()) {
							actionBar.hide();
							setFullscreen(true);
						} else {
							actionBar.show();
							setFullscreen(false);
						}
					}
				});
			}

			@Override
			public String[] usedFields() {
				return new String[] { Client.AlbumEntry.META_KEYWORDS, Client.AlbumEntry.ENTRY_URI };
			}
		};
	}

	private void placeNewTag(final String text) {
		int replaceCandidate = -1;
		int leastClickCount = Integer.MAX_VALUE;
		for (int i = 0; i < visibleKeywords.length; i++) {
			if (text.equals(visibleKeywords[i])) {
				// not place any already visible keyword
				return;
			}
			final Integer savedClickCount = tagHeatMap.get(visibleKeywords[i]);
			final int clicked = savedClickCount == null ? 0 : savedClickCount.intValue();
			if (clicked <= leastClickCount) {
				leastClickCount = clicked;
				replaceCandidate = i;
			}
		}
		visibleKeywords[replaceCandidate] = text;
		updateVisibleKeywords(visibleKeywords);
	}

	private void registerTagTouched(final String keyWord) {
		synchronized (tagHeatMap) {
			final Integer oldValue = tagHeatMap.get(keyWord);
			if (oldValue == null) {
				tagHeatMap.put(keyWord, Integer.valueOf(1));
			} else {
				tagHeatMap.put(keyWord, Integer.valueOf(oldValue.intValue() + 1));
			}
		}
	}

	private void setFullscreen(final boolean fullscreen) {
		getWindow().setFlags(fullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	private void setupActionBar() {
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		final Tab plainViewTab = actionBar.newTab().setText(R.string.plain_photo_view).setTabListener(new TabListener() {

			@Override
			public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = false;
			}

			@Override
			public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = false;
			}

			@Override
			public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = false;
			}
		});
		actionBar.addTab(plainViewTab);
		final Tab editTagsTab = actionBar.newTab().setText(R.string.edit_tags).setTabListener(new TabListener() {

			@Override
			public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = true;
			}

			@Override
			public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = true;

				showTagTab(true);
				// updateView();
			}

			@Override
			public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = false;
				showTagTab(false);
				// updateView();
			}
		});
		actionBar.addTab(editTagsTab);
	}

	private void showTagTab(final boolean visibility) {
		this.tagsVisible = visibility;
		updateAllTagViews(new TagViewHandlerUpdater() {

			@Override
			public void doUpdate(final TagViewHandler handler) {
				handler.showTags(visibility);
			}
		});
	}

	private void updateAllTagViews(final TagViewHandlerUpdater updater) {
		for (final Iterator<WeakReference<TagViewHandler>> tagIterator = tagHandlers.iterator(); tagIterator.hasNext();) {
			final WeakReference<TagViewHandler> reference = tagIterator.next();
			final TagViewHandler value = reference.get();
			if (value == null) {
				tagIterator.remove();
			} else {
				updater.doUpdate(value);
			}
		}
	}

	private void updateKeyword(final String entryUri, final String keyWord, final boolean enabled) {
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(final Void... params) {
				final ContentResolver contentResolver = getContentResolver();
				final Uri uri = Uri.parse(entryUri);
				final Cursor query = contentResolver.query(uri, new String[] { Client.AlbumEntry.META_KEYWORDS }, null, null, null);
				if (query == null || !query.moveToFirst()) {
					return null;
				}
				final Collection<String> keywords = new HashSet<String>(Client.AlbumEntry.decodeKeywords(query.getString(0)));
				if (enabled) {
					keywords.add(keyWord);
				} else {
					keywords.remove(keyWord);
				}
				final ContentValues values = new ContentValues();
				values.put(Client.AlbumEntry.META_KEYWORDS, Client.AlbumEntry.encodeKeywords(keywords));
				contentResolver.update(uri, values, null, null);
				return null;
			}
		}.execute();
	}

	private void updateVisibleKeywords(final String[] visibleKeywords) {
		this.visibleKeywords = visibleKeywords;
		updateAllTagViews(new TagViewHandlerUpdater() {

			@Override
			public void doUpdate(final TagViewHandler handler) {
				handler.setVisibleTags(visibleKeywords);
			}
		});
	}

}
