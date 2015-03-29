/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * TODO: add type comment.
 *
 */
public class SquareLinearLayout extends LinearLayout {
	/**
	 * @param context
	 */
	public SquareLinearLayout(final Context context) {
		super(context);
	}

	public SquareLinearLayout(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	public SquareLinearLayout(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, widthMeasureSpec);
	}
}
