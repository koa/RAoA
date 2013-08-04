/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.util.Map;

import android.content.Context;
import android.view.View;

public interface ViewHandler<V extends View> {

	String[] usedFields();

	int[] affectedViews();

	void bindView(final V[] views, final Context context, final Map<String, Object> values);
}