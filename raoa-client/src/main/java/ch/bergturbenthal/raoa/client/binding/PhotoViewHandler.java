/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
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
public class PhotoViewHandler implements ViewHandler<View> {
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
	private int[] affectedViews;
	private final Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private final Executor executor;
	private final int imageViewId;

	private final String persistentCachePrefix;

	private final Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();

	private final AtomicInteger storeCounter = new AtomicInteger();

	Pair<Integer, Integer> targetSize = null;
	private final TargetSizeCalculator targetSizeCalculator;
	private final String uriColumn;

	public PhotoViewHandler(final int viewId, final String uriColumn, final TargetSizeCalculator targetSizeCalculator, final Executor executor,
													final String persistentCachePrefix) {
		this.imageViewId = viewId;
		this.uriColumn = uriColumn;
		this.targetSizeCalculator = targetSizeCalculator;
		this.executor = executor;
		this.persistentCachePrefix = persistentCachePrefix;
		affectedViews = new int[] { imageViewId };
	}

	@Override
	public int[] affectedViews() {
		return affectedViews;
	}

	@Override
	public void bindView(final View[] views, final Context context, final Map<String, Object> values) {
		calculateTargetSize(context);
		final ImageView imageView = (ImageView) views[0];
		final View idleView = views.length > 1 ? views[1] : null;
		final String thumbnailUriString = (String) values.get(uriColumn);

		final AsyncTask<Void, Void, Void> runningOldTask = runningBgTasks.get(imageView);

		if (runningOldTask != null) {
			runningOldTask.cancel(false);
		}

		// skip this entry
		if (thumbnailUriString == null) {
			showBitmap(imageView, idleView, null);
			return;
		}

		final SoftReference<Bitmap> cacheReference = bitmapCache.get(thumbnailUriString);
		if (cacheReference != null) {
			final Bitmap cachedBitmap = cacheReference.get();
			if (cachedBitmap != null) {
				showBitmap(imageView, idleView, cachedBitmap);
				return;
			}
		}
		final Bitmap cachedBitmap = loadFromCache(thumbnailUriString, context);
		if (cachedBitmap != null) {
			showBitmap(imageView, idleView, cachedBitmap);
			return;
		}
		showBitmap(imageView, idleView, null);

		final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap bitmap;

			@Override
			protected Void doInBackground(final Void... params) {
				final Uri uri = Uri.parse(thumbnailUriString);
				try {
					final Bitmap persistentBitmap = loadFromPersistentCache(thumbnailUriString, context);
					if (persistentBitmap == null) {
						bitmap = loadImage(context, uri);
						storeToCache(thumbnailUriString, bitmap, context);
					} else {
						bitmap = persistentBitmap;
					}
				} catch (final Throwable t) {
					Log.i(TAG, "Cannot load image from " + uri, t);
					bitmap = null;
				}
				return null;
			}

			@Override
			protected void onPostExecute(final Void result) {
				showBitmap(imageView, idleView, bitmap);
				runningBgTasks.remove(imageView);
			}
		};
		runningBgTasks.put(imageView, asyncTask);
		asyncTask.executeOnExecutor(executor);

	}

	private void calculateTargetSize(final Context context) {
		if (targetSize == null && targetSizeCalculator != null) {
			targetSize = targetSizeCalculator.evaluateTargetSize(context);
		}
	}

	private File createCacheFile(final String thumbnailUriString, final Context context) {
		final File cacheDir = new File(context.getCacheDir(), persistentCachePrefix);
		final StringBuilder filenameSb = createFilename(thumbnailUriString);
		final File targetFile = new File(cacheDir, filenameSb.toString());
		if (!targetFile.getParentFile().exists()) {
			targetFile.getParentFile().mkdirs();
		}
		return targetFile;
	}

	private StringBuilder createFilename(final String thumbnailUriString) {
		final StringBuilder filenameSb = new StringBuilder(thumbnailUriString.replace('/', '_'));
		if (targetSize != null) {
			filenameSb.append("-");
			filenameSb.append(targetSize.first);
			filenameSb.append("-");
			filenameSb.append(targetSize.second);
		}
		return filenameSb;
	}

	private Bitmap loadFromCache(final String thumbnailUriString, final Context context) {
		final SoftReference<Bitmap> ramCacheReference = bitmapCache.get(thumbnailUriString);
		if (ramCacheReference != null) {
			final Bitmap cachedBitmap = ramCacheReference.get();
			if (cachedBitmap != null) {
				return cachedBitmap;
			}
		}
		return null;
	}

	private Bitmap loadFromPersistentCache(final String thumbnailUriString, final Context context) {
		if (persistentCachePrefix != null) {
			final File cacheDir = new File(context.getCacheDir(), persistentCachePrefix);
			final File persistentCachedBitmap = new File(cacheDir, createFilename(thumbnailUriString).toString());
			if (persistentCachedBitmap.exists()) {
				try {
					return BitmapFactory.decodeFile(persistentCachedBitmap.getAbsolutePath());
				} catch (final Exception e) {
					Log.w(TAG, "Cannot read Bitmap from Cache", e);
				}
			}
		}
		return null;
	}

	private Bitmap loadImage(final Context context, final Uri uri) throws FileNotFoundException, IOException {
		// get the real image
		final ContentResolver contentResolver = context.getContentResolver();
		final String contentType = contentResolver.getType(uri);
		if (contentType == null) {
			return null;
		}
		if (contentType.startsWith("video")) {
			final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(context, uri);
				return retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC);
			} catch (final IllegalArgumentException ex) {
				// Assume this is a corrupt video file
				return null;
			} catch (final RuntimeException ex) {
				// Assume this is a corrupt video file.
				return null;
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
				return BitmapFactory.decodeStream(imageStream, null, options);

			} finally {
				if (imageStream != null) {
					imageStream.close();
				}
			}
		}
	}

	public void preloadCache(final Context context, final Collection<String> images) {
		calculateTargetSize(context);
		for (final String imageUri : images) {
			final Uri uri = Uri.parse(imageUri);
			final File targetFile = createCacheFile(imageUri, context);
			if (targetFile.exists()) {
				continue;
			}
			executor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						final Bitmap bitmap = loadImage(context, uri);
						bitmapCache.put(imageUri, new SoftReference<Bitmap>(bitmap));
						saveCacheEntry(bitmap, targetFile);
					} catch (final Throwable t) {
						Log.i(TAG, "Cannot load image from " + uri, t);
					}
				}
			});
		}
	}

	private void saveCacheEntry(final Bitmap bitmap, final File targetFile) {
		final File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + "-" + storeCounter.incrementAndGet());
		try {
			final OutputStream outputStream = new FileOutputStream(tempFile);
			try {
				bitmap.compress(CompressFormat.JPEG, 70, outputStream);
			} finally {
				outputStream.close();
			}
			tempFile.renameTo(targetFile);
		} catch (final IOException e) {
			Log.w(TAG, "Cannot store to cache ", e);
		} finally {
			if (tempFile.exists()) {
				tempFile.delete();
			}
		}
	}

	public void setIdleView(final int idleViewId) {
		affectedViews = new int[] { imageViewId, idleViewId };
	}

	private void showBitmap(final ImageView imageView, final View idleView, final Bitmap bitmap) {
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
			if (idleView != null) {
				idleView.setVisibility(View.GONE);
				imageView.setVisibility(View.VISIBLE);
			}
		} else {
			if (idleView != null) {
				imageView.setVisibility(View.GONE);
				idleView.setVisibility(View.VISIBLE);
			} else {
				imageView.setImageResource(android.R.drawable.picture_frame);
			}
		}
	}

	private void storeToCache(final String thumbnailUriString, final Bitmap bitmap, final Context context) {
		if (bitmap == null) {
			return;
		}
		bitmapCache.put(thumbnailUriString, new SoftReference<Bitmap>(bitmap));
		if (persistentCachePrefix != null) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					final File targetFile = createCacheFile(thumbnailUriString, context);
					saveCacheEntry(bitmap, targetFile);
				}
			});
		}
	}

	@Override
	public String[] usedFields() {
		return new String[] { uriColumn };
	}

}
