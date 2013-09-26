/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.util;

import android.os.AsyncTask;
import android.util.Log;

/**
 * TODO: add type comment.
 * 
 */
public abstract class SimpleAsync extends AsyncTask<Void, Void, Void> {

	private static final String TAG = "SimpleAsync";

	protected abstract void doInBackground();

	@Override
	protected Void doInBackground(final Void... params) {
		try {
			doInBackground();
		} catch (final Throwable ex) {
			Log.e(TAG, "Error in Background", ex);
		}
		return null;
	}

	protected void onPostExecute() {
	}

	@Override
	protected void onPostExecute(final Void result) {
		try {
			onPostExecute();
		} catch (final Throwable ex) {
			Log.e(TAG, "Error on PostExecute", ex);
		}
	}

}
