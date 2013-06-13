package ch.bergturbenthal.raoa.client.photo;

import android.content.Context;
import android.graphics.Point;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class PhotoDetailContainer extends FrameLayout implements ViewPager.OnPageChangeListener {

	boolean mNeedsRedraw = false;
	private final Point mCenter = new Point();

	private final Point mInitialTouch = new Point();

	private ViewPager mPager;
	private OnPageChangeListener pageChangeListener;

	public PhotoDetailContainer(final Context context) {
		super(context);
		init();
	}

	public PhotoDetailContainer(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public PhotoDetailContainer(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public ViewPager getViewPager() {
		return mPager;
	}

	@Override
	public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
		// Force the container to redraw on scrolling.
		// Without this the outer pages render initially and then stay static
		if (mNeedsRedraw) {
			invalidate();
		}
		if (pageChangeListener != null) {
			pageChangeListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
		}
	}

	@Override
	public void onPageScrollStateChanged(final int state) {
		mNeedsRedraw = (state != ViewPager.SCROLL_STATE_IDLE);
		if (pageChangeListener != null) {
			pageChangeListener.onPageScrollStateChanged(state);
		}
	}

	@Override
	public void onPageSelected(final int position) {
		if (pageChangeListener != null) {
			pageChangeListener.onPageSelected(position);
		}
	}

	@Override
	public boolean onTouchEvent(final MotionEvent ev) {
		// We capture any touches not already handled by the ViewPager
		// to implement scrolling from a touch outside the pager bounds.
		switch (ev.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mInitialTouch.x = (int) ev.getX();
			mInitialTouch.y = (int) ev.getY();
		default:
			ev.offsetLocation(mCenter.x - mInitialTouch.x, mCenter.y - mInitialTouch.y);
			break;
		}

		return mPager.dispatchTouchEvent(ev);
	}

	public void setOnPageChangeListener(final OnPageChangeListener pageChangeListener) {
		this.pageChangeListener = pageChangeListener;
	}

	@Override
	protected void onFinishInflate() {
		try {
			mPager = (ViewPager) getChildAt(0);
			mPager.setOnPageChangeListener(this);
		} catch (final Exception e) {
			throw new IllegalStateException("The root child of PagerContainer must be a ViewPager");
		}
	}

	@Override
	protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		mCenter.x = w / 2;
		mCenter.y = h / 2;
	}

	private void init() {
		// Disable clipping of children so non-selected pages are visible
		setClipChildren(false);

		// Child clipping doesn't work with hardware acceleration in Android 3.x/4.x
		// You need to set this value here if using hardware acceleration in an
		// application targeted at these releases.
		// setLayerType(View.LAYER_TYPE_SOFTWARE, null);
	}
}
