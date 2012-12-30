package ch.royalarchive.androidclient.albumoverview;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import ch.bergturbenthal.image.provider.Client;
import ch.royalarchive.androidclient.R;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class AlbumOverviewViewBinder implements ViewBinder {
	
	private Map<String, SoftReference<Bitmap>> bitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>();
	private Map<View, AsyncTask<Void, Void, Void>> runningBgTasks = new WeakHashMap<View, AsyncTask<Void, Void, Void>>();
	
	private View actView;
	
	@Override
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
		actView = view;
		
		if (columnIndex != 2) {
			return false;
		}
		
		AsyncTask<Void, Void, Void> runningOldTask = runningBgTasks.get(view);
		
		if (runningOldTask != null) {
			runningOldTask.cancel(false);
		}
		
		final String thumbnailUriString = cursor.getString(cursor.getColumnIndex(Client.Album.THUMBNAIL));
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
				try {
					// get the real image
					InputStream imageStream = actView.getContext().getContentResolver().openInputStream(uri);
					try {
						int imageLength = actView.getContext().getResources().getDimensionPixelSize(R.dimen.image_width);

						Bitmap fullBitmap = BitmapFactory.decodeStream(imageStream);
						double scaleX = 1.0 * imageLength / fullBitmap.getWidth();
						double scaleY = 1.0 * imageLength / fullBitmap.getHeight();
						double scale = Math.max(scaleX, scaleY);

						bitmap = Bitmap.createScaledBitmap(fullBitmap, (int) Math.round(fullBitmap.getWidth() * scale),
								(int) Math.round(fullBitmap.getHeight() * scale), true);
						bitmapCache.put(thumbnailUriString, new SoftReference<Bitmap>(bitmap));
						return null;
					} finally {
						imageStream.close();
					}
				} catch (IOException e) {
					throw new RuntimeException("Cannot load image", e);
				}
			}

			@Override
			protected void onPostExecute(Void result) {
				imageView.setImageBitmap(bitmap);
				runningBgTasks.remove(imageView);
			}
		};
		runningBgTasks.put(view, asyncTask);
		asyncTask.execute();
		return true;
	}

}
