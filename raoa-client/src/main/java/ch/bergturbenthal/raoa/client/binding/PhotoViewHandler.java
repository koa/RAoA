/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.binding;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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
import android.util.LruCache;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import ch.bergturbenthal.raoa.client.util.BitmapUtil;

/**
 * Display a Photo on a ImageView
 *
 */
public class PhotoViewHandler implements ViewHandler<View>, Closeable {
	public static class DimensionCalculator implements TargetSizeCalculator {
		private final int	dimension;

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
	public static TargetSizeCalculator	                 FULLSCREEN_CALCULATOR	    = new TargetSizeCalculator() {

		                                                                                @Override
		                                                                                public Pair<Integer, Integer> evaluateTargetSize(final Context context) {
			                                                                                // Get window manager
			                                                                                final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			                                                                                // Get display size
			                                                                                final DisplayMetrics displaymetrics = new DisplayMetrics();
			                                                                                wm.getDefaultDisplay().getMetrics(displaymetrics);
			                                                                                return new Pair<Integer, Integer>(Integer.valueOf(displaymetrics.widthPixels),
			                                                                                                                  Integer.valueOf(displaymetrics.heightPixels));
		                                                                                }
	                                                                                };
	private static final String	                         TAG	                      = "PhotoViewHandler";

	private int[]	                                       affectedViews;
	private final Map<String, SoftReference<Bitmap>>	   bitmapCache	              = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private final File	                                 cacheRootDir;
	private final Executor	                             executor;

	private final int	                                   imageViewId;

	private final LruCache<String, File>	               persistentBitmapCacheFiles	= new LruCache<String, File>(1024 * 1024) {

		                                                                                @Override
		                                                                                protected File create(final String key) {
			                                                                                return new File(cacheRootDir, key);
		                                                                                }

		                                                                                @Override
		                                                                                protected void entryRemoved(final boolean evicted,
		                                                                                                            final String key,
		                                                                                                            final File oldValue,
		                                                                                                            final File newValue) {
			                                                                                oldValue.delete();
		                                                                                }

		                                                                                @Override
		                                                                                protected int sizeOf(final String key, final File value) {
			                                                                                if (value.exists()) {
				                                                                                return (int) value.length() / 1024;
			                                                                                }
			                                                                                return 0;
		                                                                                }

	                                                                                };

	private final AtomicBoolean	                         running	                  = new AtomicBoolean(true);

	private final Map<View, AsyncTask<Void, Void, Void>>	runningBgTasks	          = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();
	private final AtomicInteger	                         storeCounter	              = new AtomicInteger();
	Pair<Integer, Integer>	                             targetSize	                = null;
	private final TargetSizeCalculator	                 targetSizeCalculator;
	private final String	                               uriColumn;

	private final boolean	                               usePersistentCache;

	public PhotoViewHandler(final Context context, final int viewId, final String uriColumn, final TargetSizeCalculator targetSizeCalculator, final Executor executor,
	                        final String persistentCachePrefix) {
		this.imageViewId = viewId;
		this.uriColumn = uriColumn;
		this.targetSizeCalculator = targetSizeCalculator;
		this.executor = executor;
		usePersistentCache = persistentCachePrefix != null;
		affectedViews = new int[] { imageViewId };
		calculateTargetSize(context);
		cacheRootDir = new File(context.getCacheDir(), "thumbnail-cache");
		if (usePersistentCache) {
			if (!cacheRootDir.exists()) {
				cacheRootDir.mkdirs();
			}
			for (final File f : cacheRootDir.listFiles()) {
				persistentBitmapCacheFiles.get(f.getName());
			}
		}
	}

	@Override
	public int[] affectedViews() {
		return affectedViews;
	}

	@Override
	public void bindView(final View[] views, final Context context, final Map<String, Object> values) {

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

		if (usePersistentCache) {
			final File persistentCachedBitmap = persistentBitmapCacheFiles.get(createFilename(thumbnailUriString));
			if (persistentCachedBitmap.exists()) {
				final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

					private Bitmap	bitmap;

					@Override
					protected Void doInBackground(final Void... params) {
						bitmap = BitmapFactory.decodeFile(persistentCachedBitmap.getAbsolutePath());
						return null;
					}

					@Override
					protected void onPostExecute(final Void result) {
						displayLoadedImage(bitmap, imageView, idleView);
					}
				};
				runningBgTasks.put(imageView, asyncTask);
				asyncTask.execute();
			} else {
				loadFullyFromProvider(context, imageView, idleView, thumbnailUriString);
			}
		} else {
			loadFullyFromProvider(context, imageView, idleView, thumbnailUriString);
		}

	}

	private void calculateTargetSize(final Context context) {
		if (targetSize == null && targetSizeCalculator != null) {
			targetSize = targetSizeCalculator.evaluateTargetSize(context);
		}
	}

	@Override
	public void close() {
		running.set(false);
	}

	private String createFilename(final String thumbnailUriString) {
		final StringBuilder filenameSb = new StringBuilder(thumbnailUriString.replace('/', '_'));
		if (targetSize != null) {
			filenameSb.append("-");
			filenameSb.append(targetSize.first);
			filenameSb.append("-");
			filenameSb.append(targetSize.second);
		}
		return filenameSb.toString();
	}

	private void displayLoadedImage(final Bitmap bitmap, final ImageView imageView, final View idleView) {
		showBitmap(imageView, idleView, bitmap);
		runningBgTasks.remove(imageView);
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

	private Bitmap loadFromPersistentCache(final String thumbnailUriString) {
		if (usePersistentCache) {
			final File persistentCachedBitmap = persistentBitmapCacheFiles.get(createFilename(thumbnailUriString));
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

	private void loadFullyFromProvider(final Context context, final ImageView imageView, final View idleView, final String thumbnailUriString) {
		final AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {

			private Bitmap	bitmap;

			@Override
			protected Void doInBackground(final Void... params) {
				if (!running.get()) {
					return null;
				}
				final Uri uri = Uri.parse(thumbnailUriString);
				try {
					final Bitmap persistentBitmap = loadFromPersistentCache(thumbnailUriString);
					if (persistentBitmap == null) {
						bitmap = loadImage(context, uri);
						storeToCache(thumbnailUriString, bitmap, context);
					} else {
						bitmap = persistentBitmap;
					}
				} catch (final FileNotFoundException e) {
					// file not already loaded
					bitmap = null;
				} catch (final Throwable t) {
					Log.i(TAG, "Cannot load image from " + uri, t);
					bitmap = null;
				}
				return null;
			}

			@Override
			protected void onPostExecute(final Void result) {
				if (running.get()) {
					displayLoadedImage(bitmap, imageView, idleView);
				}
			}
		};
		runningBgTasks.put(imageView, asyncTask);
		asyncTask.executeOnExecutor(executor);
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

	public void preloadCache(final Context context, final List<String> images) {
		calculateTargetSize(context);
		int remainingJobCount = 5;
		int nextIndex = 0;
		while (nextIndex < images.size()) {
			final int startIndex = nextIndex;
			nextIndex = startIndex + (images.size() - startIndex) / remainingJobCount--;
			final List<String> jobImageList = images.subList(startIndex, nextIndex);
			executor.execute(new Runnable() {

				@Override
				public void run() {
					for (final String imageUri : jobImageList) {
						if (!running.get()) {
							break;
						}
						if (imageUri == null) {
							continue;
						}
						final Uri uri = Uri.parse(imageUri);
						final File targetFile = persistentBitmapCacheFiles.get(createFilename(imageUri));
						if (targetFile.exists()) {
							continue;
						}
						try {
							final Bitmap bitmap = loadImage(context, uri);
							bitmapCache.put(imageUri, new SoftReference<Bitmap>(bitmap));
							saveCacheEntry(bitmap, targetFile);
						} catch (final FileNotFoundException e) {
							// file not ready now
						} catch (final Throwable t) {
							Log.i(TAG, "Cannot load image from " + uri, t);
						}
					}
				}
			});
		}
	}

	private void saveCacheEntry(final Bitmap bitmap, final File targetFile) {
		if (bitmap == null) {
			targetFile.delete();
			return;
		}
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
		if (usePersistentCache) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					final File targetFile = persistentBitmapCacheFiles.get(createFilename(thumbnailUriString));
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
