/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import ch.bergturbenthal.raoa.R;
import ch.bergturbenthal.raoa.client.util.BitmapUtil;

/**
 * Display a Photo on a ImageView
 * 
 */
public class PhotoViewHandler extends AbstractViewHandler<ImageView> {

	private static final String TAG = "PhotoViewHandler";
	private final Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private final Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();
	private final String tagColumn;
	private final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5, 15, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1000));
	private final String uriColumn;

	public PhotoViewHandler(final int viewId, final String uriColumn, final String tagColumn) {
		super(viewId);
		this.uriColumn = uriColumn;
		this.tagColumn = tagColumn;
	}

	@Override
	public void bindView(final ImageView view, final Context context, final Map<String, Object> values) {
		final String thumbnailUriString = (String) values.get(uriColumn);

		final AsyncTask<Void, Void, Void> runningOldTask = runningBgTasks.get(view);

		if (runningOldTask != null) {
			runningOldTask.cancel(false);
		}

		final ImageView imageView = view;
		// skip this entry
		if (thumbnailUriString == null) {
			imageView.setImageResource(android.R.drawable.picture_frame);
			return;
		}

		final SoftReference<Bitmap> cacheReference = bitmapCache.get(thumbnailUriString);
		if (cacheReference != null) {
			final Bitmap cachedBitmap = cacheReference.get();
			if (cachedBitmap != null) {
				imageView.setImageBitmap(cachedBitmap);
				return;
			}
		}
		imageView.setImageResource(android.R.drawable.picture_frame);
		final Uri uri = Uri.parse(thumbnailUriString);

		final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap bitmap;
			private final boolean isDetailView = false;

			@Override
			protected Void doInBackground(final Void... params) {
				try {
					// get the real image
					InputStream imageStream = view.getContext().getContentResolver().openInputStream(uri);
					try {
						final int width;
						final int heigth;
						// Calculate inSampleSize
						if (isDetailView) {
							// Get window manager
							final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
							// Get display size
							final DisplayMetrics displaymetrics = new DisplayMetrics();
							wm.getDefaultDisplay().getMetrics(displaymetrics);
							width = displaymetrics.widthPixels;
							heigth = displaymetrics.heightPixels;
						} else {
							width = heigth = view.getContext().getResources().getDimensionPixelSize(R.dimen.image_width);
						}

						// First decode with inJustDecodeBounds=true to check dimensions
						final BitmapFactory.Options options = new BitmapFactory.Options();
						options.inJustDecodeBounds = true;
						BitmapFactory.decodeStream(imageStream, null, options);

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

	}

	@Override
	public String[] usedFields() {
		return new String[] { uriColumn, tagColumn };
	}
}
