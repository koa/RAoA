package ch.royalarchive.androidclient;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class OverviewBinder implements ViewBinder {

	private static String TAG = OverviewBinder.class.getSimpleName();
	public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
			5, 15, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1000),
			new ThreadFactory() {
				private final AtomicInteger mCount = new AtomicInteger(1);

				public Thread newThread(Runnable r) {
					return new Thread(r, "OverviewBinder #"
							+ mCount.getAndIncrement());
				}
			});

	private Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();

	private boolean isDetailView = false;

	public OverviewBinder(boolean isDetailView) {
		this.isDetailView = isDetailView;
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

		SoftReference<Bitmap> cacheReference = bitmapCache
				.get(thumbnailUriString);
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
				try {
					// get the real image
					ContentResolver contentResolver = view.getContext()
							.getContentResolver();
					String type = contentResolver.getType(uri);
					if (type.startsWith("image/")) {
						InputStream imageStream = contentResolver
								.openInputStream(uri);
						try {
							int dimen_width = R.dimen.image_width;
							if (isDetailView) {
								dimen_width = R.dimen.image_detail_width;
							}
							int imageLength = view.getContext().getResources()
									.getDimensionPixelSize(dimen_width);

							Bitmap fullBitmap = BitmapFactory
									.decodeStream(imageStream);
							double scaleX = 1.0 * imageLength
									/ fullBitmap.getWidth();
							double scaleY = 1.0 * imageLength
									/ fullBitmap.getHeight();
							double scale = Math.max(scaleX, scaleY);

							bitmap = Bitmap.createScaledBitmap(
									fullBitmap,
									(int) Math.round(fullBitmap.getWidth()
											* scale),
									(int) Math.round(fullBitmap.getHeight()
											* scale), true);
							bitmapCache.put(thumbnailUriString,
									new SoftReference<Bitmap>(bitmap));
						} finally {
							if (imageStream != null) {
								imageStream.close();
							}
						}
					} else if (type.startsWith("video/")) {
						MediaMetadataRetriever retriever = new MediaMetadataRetriever();
						retriever.setDataSource(view.getContext(), uri);
						bitmap = retriever.getFrameAtTime();
						bitmapCache.put(thumbnailUriString,
								new SoftReference<Bitmap>(bitmap));
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
		asyncTask.executeOnExecutor(THREAD_POOL_EXECUTOR);
		return true;
	}

}
