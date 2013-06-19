/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.util;

import android.os.AsyncTask;

/**
 * TODO: add type comment.
 * 
 */
public abstract class SimpleAsync extends AsyncTask<Void, Void, Void> {

	protected abstract void doInBackground();

	@Override
	protected Void doInBackground(final Void... params) {
		doInBackground();
		return null;
	}

	protected void onPostExecute() {
	}

	@Override
	protected void onPostExecute(final Void result) {
		onPostExecute();
	}

}
