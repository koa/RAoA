/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Map;

import android.content.Context;
import android.view.View;

public interface ViewHandler<V extends View> {
	void setContext(final Context context);

	String[] usedFields();

	int affectedView();

	void bindView(final V view, final Map<String, Object> values);
}