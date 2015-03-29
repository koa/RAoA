/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Map;

import android.content.Context;
import android.widget.TextView;

public class TextViewHandler extends AbstractViewHandler<TextView> {

	private final String	column;

	/**
	 * @param affectedView
	 */
	public TextViewHandler(final int affectedView, final String column) {
		super(affectedView);
		this.column = column;
	}

	@Override
	public void bindView(final TextView view, final Context context, final Map<String, Object> values) {
		view.setText(String.valueOf(values.get(column)));
	}

	@Override
	public String[] usedFields() {
		return new String[] { column };
	}
}
