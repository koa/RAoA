/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import android.view.View;

/**
 * TODO: add type comment.
 * 
 * @param <V>
 * 
 */
public abstract class AbstractViewHandler<V extends View> implements ViewHandler<V> {

	private final int affectedView;

	protected AbstractViewHandler(final int affectedView) {
		this.affectedView = affectedView;
	}

	@Override
	public int affectedView() {
		return affectedView;
	}

}