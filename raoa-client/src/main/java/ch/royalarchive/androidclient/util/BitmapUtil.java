package ch.royalarchive.androidclient.util;

import android.graphics.BitmapFactory;

public class BitmapUtil {

	public static int calculateInSampleSize(final BitmapFactory.Options options, final int reqWidth, final int reqHeight) {
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize_width = 1;
		int inSampleSize_height = 1;

		if (height > reqHeight || width > reqWidth) {
			inSampleSize_width = (int) Math.floor((float) width / (float) reqWidth);
			inSampleSize_height = (int) Math.floor((float) height / (float) reqHeight);
		}
		return Math.max(1, Math.max(inSampleSize_width, inSampleSize_height));
	}

}
