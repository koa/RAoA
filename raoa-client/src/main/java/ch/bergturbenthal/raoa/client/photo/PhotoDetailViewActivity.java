package ch.bergturbenthal.raoa.client.photo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout.LayoutParams;
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
				final LinearLayout overlayLayout = (LinearLayout) view.findViewById(R.id.photo_edit_overlay_layout);

				if (isOverlayVisible) {
					// overlayLayout.removeAllViewsInLayout();
					overlayLayout.setVisibility(View.VISIBLE);
					final List<String> knownKeywords = KeywordUtil.getKnownKeywords(getContentResolver());
					final int max = knownKeywords.size() < 5 ? knownKeywords.size() : 5;
					for (int i = 0; i < max; i++) {
						final ToggleButton toggleButton = new ToggleButton(context);
						final String tag = knownKeywords.get(i);
						toggleButton.setSingleLine();
						toggleButton.setText(tag);
						toggleButton.setTextOff(tag);
						toggleButton.setTextOn(tag);
						toggleButton.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
						if (toggleButton.isChecked()) {
							toggleButton.setBackgroundColor(Color.GREEN);
						} else {
							toggleButton.setBackgroundColor(Color.GRAY);
						}

						toggleButton.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(final View v) {
								final boolean isChecked = ((ToggleButton) v).isChecked();
								if (isChecked) {
									((ToggleButton) v).setBackgroundColor(Color.GREEN);

								} else {
									((ToggleButton) v).setBackgroundColor(Color.GRAY);
								}

								overlayLayout.getRootView().requestLayout();
								overlayLayout.invalidate();
							}
						});
						overlayLayout.addView(toggleButton);
					}

				} else {
					overlayLayout.setVisibility(View.INVISIBLE);
				}
				overlayLayout.getRootView().requestLayout();
				overlayLayout.invalidate();
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
						} else {
							actionBar.show();
						}
					}
				});
			}

			@Override
			public String[] usedFields() {
				return new String[] {};
			}
		});
		return ret;
	}

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
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(ACTUAL_POS, ((CurserPagerAdapter) pager.getAdapter()).getCurrentPosition());
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

	protected final void reloadViewPager() {
		final int currentItem = pager.getCurrentItem();
		pager.setAdapter(adapter);
		pager.setCurrentItem(currentItem, false);
	}

	private void setFullscreen(final boolean fullscreen) {
		getWindow().setFlags(fullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	private void setupActionBar() {
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		final Tab editTagsTab = actionBar.newTab().setText(R.string.edit_tags).setTabListener(new TabListener() {

			@Override
			public void onTabReselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = true;
			}

			@Override
			public void onTabSelected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = true;
				reloadViewPager();
			}

			@Override
			public void onTabUnselected(final Tab tab, final FragmentTransaction ft) {
				isOverlayVisible = false;
				reloadViewPager();
			}
		});
		actionBar.addTab(editTagsTab);
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
	}
}
