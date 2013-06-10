package ch.bergturbenthal.raoa.client.photo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ToggleButton;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.CurserPagerAdapter;
import ch.bergturbenthal.raoa.client.binding.PhotoViewHandler;
import ch.bergturbenthal.raoa.client.binding.ViewHandler;
import ch.bergturbenthal.raoa.client.util.KeywordUtil;
import ch.bergturbenthal.raoa.provider.Client;

public class PhotoDetailViewActivity extends Activity {

	private static interface TagViewHandler {
		void setVisibleTags(final String[] tags);

		void showTags(final boolean visibility);
	}

	private static interface TagViewHandlerUpdater {
		void doUpdate(final TagViewHandler handler);
	}

	protected static final int VISIBLE_KEYWORD_COUNT = 5;

	private static final String ACTUAL_POS = "actPos";

	private static final String ALBUM_ID = "album_uri";

	private static final String CURR_ITEM_INDEX = "currentItemIndex";

	private static final String[] PROJECTION = new String[] { Client.AlbumEntry.THUMBNAIL };
	private int actPos;

	private CurserPagerAdapter adapter;
	private Uri albumUri;

	private PhotoDetailContainer detailContainer;

	private boolean isOverlayVisible = false;

	private ViewPager pager;

	private final Collection<WeakReference<TagViewHandler>> tagHandlers = new LinkedList<WeakReference<TagViewHandler>>();

	private String[] visibleKeywords = new String[0];

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

		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
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
			private String[] visibleKeywords;

			@Override
			protected Void doInBackground(final Void... params) {
				final List<String> knownKeywords = KeywordUtil.getKnownKeywords(getContentResolver());
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
			}
		}.execute();

		if (savedInstanceState != null) {
			actPos = savedInstanceState.getInt(ACTUAL_POS);
		}
		setupActionBar();
	}

	@Override
	public void onLowMemory() {
		decrementOffscreenPageLimit();
		super.onLowMemory();
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
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((CurserPagerAdapter) pager.getAdapter()).getCurrentPosition());
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
		ret.add(new PhotoViewHandler(R.id.photos_item_image, Client.AlbumEntry.THUMBNAIL, PhotoViewHandler.FULLSCREEN_CALCULATOR));
		ret.add(new AbstractViewHandler<View>(R.id.FrameLayout1) {

			@Override
			public void bindView(final View view, final Context context, final Map<String, Object> values) {
				final String entryUri = (String) values.get(Client.AlbumEntry.ENTRY_URI);
				final LinearLayout overlayLayout = (LinearLayout) view.findViewById(R.id.photo_edit_overlay_layout);
				final ToggleButton[] visibleToggleButtons = new ToggleButton[VISIBLE_KEYWORD_COUNT];
				for (int i = 0; i < visibleToggleButtons.length; i++) {
					final ToggleButton toggleButton = new ToggleButton(context);
					toggleButton.setSingleLine();
					final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(	android.view.ViewGroup.LayoutParams.MATCH_PARENT,
																																									android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
					params.setMargins(5, 5, 5, 5);
					toggleButton.setLayoutParams(params);
					toggleButton.setAlpha((float) 0.9);
					toggleButton.setText("<dummy>");
					toggleButton.setTextOn("<dummy>");
					toggleButton.setTextOff("<dummy>");
					// toggleButton.setVisibility(View.INVISIBLE);
					toggleButton.setBackgroundResource(R.drawable.button_color_toggle);

					toggleButton.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(final View v) {
							final boolean isChecked = ((ToggleButton) v).isChecked();
							final String keyWord = (String) v.getTag();
							if (keyWord == null) {
								return;
							}
							updateKeyword(entryUri, keyWord, isChecked);
						}

					});
					visibleToggleButtons[i] = toggleButton;
					overlayLayout.addView(toggleButton);
				}
				final Collection<String> keywordList = new HashSet<String>(Client.AlbumEntry.decodeKeywords((String) values.get(Client.AlbumEntry.META_KEYWORDS)));
				final TagViewHandler updateHandler = new TagViewHandler() {

					@Override
					public void setVisibleTags(final String[] tags) {
						for (int i = 0; i < visibleToggleButtons.length; i++) {
							if (tags.length > i && tags[i] != null) {
								final String tag = tags[i];
								visibleToggleButtons[i].setVisibility(View.VISIBLE);
								visibleToggleButtons[i].setText(tag);
								visibleToggleButtons[i].setTextOff(tag);
								visibleToggleButtons[i].setTextOn(tag);
								visibleToggleButtons[i].setTag(tag);
								visibleToggleButtons[i].setChecked(keywordList.contains(tag));
							} else {
								visibleToggleButtons[i].setVisibility(View.INVISIBLE);
							}
							visibleToggleButtons[i].invalidate();
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

				if (isOverlayVisible) {
					// overlayLayout.removeAllViewsInLayout();
					overlayLayout.setVisibility(View.VISIBLE);
				} else {
					overlayLayout.setVisibility(View.INVISIBLE);
				}

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
		});
		return ret;
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
