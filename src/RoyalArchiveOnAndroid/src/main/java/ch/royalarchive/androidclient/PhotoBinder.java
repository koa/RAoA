package ch.royalarchive.androidclient;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import ch.royalarchive.androidclient.util.BitmapUtil;

public class PhotoBinder implements ViewBinder {

	private static String TAG = PhotoBinder.class.getSimpleName();

	private Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();

	private boolean isDetailView = false;
	private Context context;

	public PhotoBinder(boolean isDetailView, Context context) {
		this.isDetailView = isDetailView;
		this.context = context;
	}

	@Override
	public boolean setViewValue(final View view, Cursor cursor, int columnIndex) {

		if (!(view instanceof ImageView)) {
			return false;
		}

		AsyncTask<Void, Void, Void> runningOldTask = runningBgTasks.get(view);

		if (runningOldTask != null) {
			runningOldTask.cancel(false);
		}

		final String thumbnailUriString = cursor.getString(columnIndex);
		final ImageView imageView = (ImageView) view;
		// skip this entry
		if (thumbnailUriString == null) {
			imageView.setImageResource(android.R.drawable.picture_frame);
			return true;
		}

		SoftReference<Bitmap> cacheReference = bitmapCache.get(thumbnailUriString);
		if (cacheReference != null) {
			Bitmap cachedBitmap = cacheReference.get();
			if (cachedBitmap != null) {
				imageView.setImageBitmap(cachedBitmap);
				return true;
			}
		}
		imageView.setImageResource(android.R.drawable.picture_frame);
		final Uri uri = Uri.parse(thumbnailUriString);

		AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap bitmap;

			@Override
			protected Void doInBackground(Void... params) {
				int width;
				int heigth;
				int orientation;

				try {
					// get the real image
					InputStream imageStream = view.getContext().getContentResolver().openInputStream(uri);
					try {

						// int dimen_width = R.dimen.image_width;
						// if (isDetailView) {
						// dimen_width = R.dimen.image_detail_width;
						// }
						// int imageLength = view.getContext().getResources().getDimensionPixelSize(dimen_width);
						//
						// Bitmap fullBitmap = BitmapFactory.decodeStream(imageStream);
						// double scaleX = 1.0 * imageLength / fullBitmap.getWidth();
						// double scaleY = 1.0 * imageLength / fullBitmap.getHeight();
						// double scale = Math.max(scaleX, scaleY);
						//
						// bitmap = Bitmap.createScaledBitmap(fullBitmap, (int) Math.round(fullBitmap.getWidth() * scale),
						// (int) Math.round(fullBitmap.getHeight() * scale), true);

						// First decode with inJustDecodeBounds=true to check dimensions
						final BitmapFactory.Options options = new BitmapFactory.Options();
						options.inJustDecodeBounds = true;
						options.inPurgeable = true;
						options.inInputShareable = true;
						BitmapFactory.decodeStream(imageStream, null, options);

						// Calculate inSampleSize
						// Get current orientation
						WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
						orientation = context.getResources().getConfiguration().orientation;

						// Get display size in pixels
						DisplayMetrics displaymetrics = new DisplayMetrics();
						wm.getDefaultDisplay().getMetrics(displaymetrics);
						width = displaymetrics.widthPixels;
						heigth = displaymetrics.heightPixels;

						if (!isDetailView) {
							width = heigth = view.getContext().getResources().getDimensionPixelSize(R.dimen.image_width);
						}

						options.inSampleSize = BitmapUtil.calculateInSampleSize(options, orientation, width, heigth);

						imageStream.close();
						imageStream = view.getContext().getContentResolver().openInputStream(uri);

						// Decode bitmap with inSampleSize set
						options.inJustDecodeBounds = false;
						bitmap = BitmapFactory.decodeStream(imageStream, null, options);

						bitmapCache.put(thumbnailUriString, new SoftReference<Bitmap>(bitmap));
					} finally {
						if (imageStream != null) {
							imageStream.close();
						}
					}
				} catch (Throwable t) {
					Log.i(TAG, "Cannot load image", t);
					bitmap = null;
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				if (bitmap != null) {
					imageView.setImageBitmap(bitmap);
				}
				runningBgTasks.remove(imageView);
			}
		};
		runningBgTasks.put(view, asyncTask);
		asyncTask.execute();
		return true;
	}

}
