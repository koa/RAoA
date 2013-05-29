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
 */
public class SetTagViewHandler extends AbstractViewHandler<View> {

	private final String columnName;

	public SetTagViewHandler(final int viewId, final String columnName) {
		super(viewId);
		this.columnName = columnName;

	}

	@Override
	public void bindView(final View view, final Context context, final Map values) {
		final Object value = values.get(columnName);
		view.setTag(value);
	}

	@Override
	public String[] usedFields() {
		return new String[] { columnName };
	}

}
