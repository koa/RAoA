package ch.royalarchive.androidclient.util;

import android.content.res.Configuration;
import android.graphics.BitmapFactory;

public class BitmapUtil {

	public static int calculateInSampleSize(BitmapFactory.Options options, int orientation, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 0;
		
		if (height > reqHeight || width > reqWidth) {
			if (orientation == Configuration.ORIENTATION_PORTRAIT) {
				inSampleSize = Math.round((float) width / (float) reqWidth);
				
			} else {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			}
		}
		return inSampleSize <= 1 ? 2 : inSampleSize;
	}

}
