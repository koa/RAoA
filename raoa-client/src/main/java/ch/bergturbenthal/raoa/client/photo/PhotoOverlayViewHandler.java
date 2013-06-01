package ch.bergturbenthal.raoa.client.photo;

import java.util.Map;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.view.View;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.binding.AbstractViewHandler;
import ch.bergturbenthal.raoa.client.binding.CurserPagerAdapter;

public class PhotoOverlayViewHandler extends AbstractViewHandler<View> {

	private CurserPagerAdapter adapter;
	private boolean isOverlayVisible = false;
	private ViewPager pager;

	protected PhotoOverlayViewHandler(final int affectedView) {
		super(affectedView);
	}

	public PhotoOverlayViewHandler(final int layout, final ViewPager pager, final CurserPagerAdapter adapter) {
		this(layout);
		this.pager = pager;
		this.adapter = adapter;
	}

	@Override
	public void bindView(final View view, final Context context, final Map<String, Object> values) {
		final View overlayLayout = view.findViewById(R.id.photo_edit_overlay_layout);

		if (isOverlayVisible) {
			overlayLayout.setVisibility(View.VISIBLE);
		} else {
			overlayLayout.setVisibility(View.INVISIBLE);
		}
		view.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(final View v) {
				isOverlayVisible = !isOverlayVisible;
				reloadViewPager();
			}

			private void reloadViewPager() {
				final int currentItem = pager.getCurrentItem();
				pager.setAdapter(adapter);
				pager.setCurrentItem(currentItem, false);
			}
		});
	}

	@Override
	public String[] usedFields() {
		return new String[] {};
	}
}
