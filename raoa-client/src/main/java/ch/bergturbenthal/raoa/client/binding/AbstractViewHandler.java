/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Map;

import android.content.Context;
import android.view.View;

/**
 * TODO: add type comment.
 *
 * @param <V>
 *
 */
public abstract class AbstractViewHandler<V extends View> implements ViewHandler<V> {

	private final int[]	affectedViews;

	protected AbstractViewHandler(final int affectedView) {
		this.affectedViews = new int[] { affectedView };
	}

	protected AbstractViewHandler(final int[] affectedViews) {
		this.affectedViews = affectedViews;
	}

	@Override
	public int[] affectedViews() {
		return affectedViews;
	}

	@Override
	public void bindView(final V[] views, final Context context, final Map<String, Object> values) {
		bindView(views[0], context, values);
	}

	protected abstract void bindView(final V view, final Context context, final Map<String, Object> values);

}