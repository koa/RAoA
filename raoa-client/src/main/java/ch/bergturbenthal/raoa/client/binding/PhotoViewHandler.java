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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import ch.bergturbenthal.raoa.client.util.BitmapUtil;

/**
 * Display a Photo on a ImageView
 * 
 */
public class PhotoViewHandler extends AbstractViewHandler<ImageView> {
	public static class DimensionCalculator implements TargetSizeCalculator {
		private final int dimension;

		public DimensionCalculator(final int dimension) {
			this.dimension = dimension;

		}

		@Override
		public Pair<Integer, Integer> evaluateTargetSize(final Context context) {
			final Integer length = Integer.valueOf(context.getResources().getDimensionPixelSize(dimension));
			return new Pair<Integer, Integer>(length, length);
		}

	}

	/**
	 * Interface for Handler to calculate image size while reading from ContentProvider.
	 * 
	 */
	public static interface TargetSizeCalculator {
		/**
		 * Calculating the width and height
		 * 
		 * @param context
		 *          Context
		 * @return a {@link Pair} with has width as first and height as second value.
		 */
		Pair<Integer, Integer> evaluateTargetSize(final Context context);
	}

	/**
	 * Scales the image to Fullscreen
	 */
	public static TargetSizeCalculator FULLSCREEN_CALCULATOR = new TargetSizeCalculator() {

		@Override
		public Pair<Integer, Integer> evaluateTargetSize(final Context context) {
			// Get window manager
			final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			// Get display size
			final DisplayMetrics displaymetrics = new DisplayMetrics();
			wm.getDefaultDisplay().getMetrics(displaymetrics);
			return new Pair<Integer, Integer>(Integer.valueOf(displaymetrics.widthPixels), Integer.valueOf(displaymetrics.heightPixels));
		}
	};

	private static final String TAG = "PhotoViewHandler";
	private final Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private final Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();
	private final TargetSizeCalculator targetSizeCalculator;
	private final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(5, 15, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1000));
	private final String uriColumn;
	private final int videoPlayButtonId;

	public PhotoViewHandler(final int viewId, final String uriColumn, final TargetSizeCalculator targetSizeCalculator, final int videoPlayButtonId) {
		super(viewId);
		this.uriColumn = uriColumn;
		this.targetSizeCalculator = targetSizeCalculator;
		this.videoPlayButtonId = videoPlayButtonId;
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

		final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap bitmap;

			@Override
			protected Void doInBackground(final Void... params) {
				final Uri uri = Uri.parse(thumbnailUriString);
				try {
					// get the real image
					final ContentResolver contentResolver = view.getContext().getContentResolver();
					final String contentType = contentResolver.getType(uri);
					if (contentType == null) {
						return null;
					}
					if (contentType.startsWith("video")) {
						final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
						try {
							retriever.setDataSource(context, uri);
							bitmap = retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC);
						} catch (final IllegalArgumentException ex) {
							// Assume this is a corrupt video file
						} catch (final RuntimeException ex) {
							// Assume this is a corrupt video file.
						} finally {
							try {
								retriever.release();
							} catch (final RuntimeException ex) {
								// Ignore failures while cleaning up.
							}
						}
					} else {
						InputStream imageStream = contentResolver.openInputStream(uri);
						try {

							// First decode with inJustDecodeBounds=true to check dimensions
							final BitmapFactory.Options options = new BitmapFactory.Options();
							options.inJustDecodeBounds = true;
							BitmapFactory.decodeStream(imageStream, null, options);
							if (targetSizeCalculator != null) {
								final Pair<Integer, Integer> targetSize = targetSizeCalculator.evaluateTargetSize(context);
								if (targetSize != null && targetSize.first != null && targetSize.second != null) {
									options.inSampleSize = BitmapUtil.calculateInSampleSize(options, targetSize.first.intValue(), targetSize.second.intValue());
								}
							}

							imageStream.close();
							imageStream = contentResolver.openInputStream(uri);

							// Decode bitmap with inSampleSize set
							options.inJustDecodeBounds = false;
							options.inPurgeable = true;
							options.inInputShareable = true;
							bitmap = BitmapFactory.decodeStream(imageStream, null, options);

						} finally {
							if (imageStream != null) {
								imageStream.close();
							}
						}
					}
					bitmapCache.put(thumbnailUriString, new SoftReference<Bitmap>(bitmap));
				} catch (final Throwable t) {
					Log.i(TAG, "Cannot load image from " + uri, t);
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
		return new String[] { uriColumn };
	}

}
