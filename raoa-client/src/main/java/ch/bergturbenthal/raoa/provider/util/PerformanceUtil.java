package ch.bergturbenthal.raoa.provider.util;

import java.util.concurrent.Callable;

import android.util.Log;

public class PerformanceUtil {
	public static <V> V reportPerformance(final String description, final Callable<V> callable) {
		final long startTime = System.currentTimeMillis();
		try {
			return callable.call();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		} finally {
			final long endTime = System.currentTimeMillis();
			final String msg = (endTime - startTime) + " ms: " + description;
			Log.i("performance", msg);
		}
	}
}
