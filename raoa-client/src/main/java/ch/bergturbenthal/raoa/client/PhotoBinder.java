package ch.bergturbenthal.raoa.client;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.util.BitmapUtil;

public class PhotoBinder implements ViewBinder {

	private static String TAG = PhotoBinder.class.getSimpleName();

	private final Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();

	private final Context context;
	private boolean isDetailView = false;

	private final Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();
	private final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5, 15, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1000));

	public PhotoBinder(final boolean isDetailView, final Context context) {
		this.isDetailView = isDetailView;
		this.context = context;
	}

	@Override
	public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {

		if (!(view instanceof ImageView)) {
			return false;
		}

		final AsyncTask<Void, Void, Void> runningOldTask = runningBgTasks.get(view);

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

		final SoftReference<Bitmap> cacheReference = bitmapCache.get(thumbnailUriString);
		if (cacheReference != null) {
			final Bitmap cachedBitmap = cacheReference.get();
			if (cachedBitmap != null) {
				imageView.setImageBitmap(cachedBitmap);
				return true;
			}
		}
		imageView.setImageResource(android.R.drawable.picture_frame);
		final Uri uri = Uri.parse(thumbnailUriString);

		final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap bitmap;

			@Override
			protected Void doInBackground(final Void... params) {
				int width;
				int heigth;

				try {
					// get the real image
					InputStream imageStream = view.getContext().getContentResolver().openInputStream(uri);
					try {
						// Get window manager
						final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
						// Get display size
						final DisplayMetrics displaymetrics = new DisplayMetrics();
						wm.getDefaultDisplay().getMetrics(displaymetrics);
						width = displaymetrics.widthPixels;
						heigth = displaymetrics.heightPixels;

						// First decode with inJustDecodeBounds=true to check dimensions
						final BitmapFactory.Options options = new BitmapFactory.Options();
						options.inJustDecodeBounds = true;
						BitmapFactory.decodeStream(imageStream, null, options);

						// Calculate inSampleSize
						if (!isDetailView) {
							width = heigth = view.getContext().getResources().getDimensionPixelSize(R.dimen.image_width);
						}

						options.inSampleSize = BitmapUtil.calculateInSampleSize(options, width, heigth);

						imageStream.close();
						imageStream = view.getContext().getContentResolver().openInputStream(uri);

						// Decode bitmap with inSampleSize set
						options.inJustDecodeBounds = false;
						options.inPurgeable = true;
						options.inInputShareable = true;
						bitmap = BitmapFactory.decodeStream(imageStream, null, options);

						bitmapCache.put(thumbnailUriString, new SoftReference<Bitmap>(bitmap));
					} finally {
						if (imageStream != null) {
							imageStream.close();
						}
					}
				} catch (final Throwable t) {
					Log.i(TAG, "Cannot load image", t);
					bitmap = null;
				}
				return null;
			}

			@Override
			protected void onPostExecute(final Void result) {
				if (bitmap != null) {
					imageView.setImageBitmap(bitmap);
				}
				runningBgTasks.remove(imageView);
			}
		};
		runningBgTasks.put(view, asyncTask);
		asyncTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
		return true;
	}

}
