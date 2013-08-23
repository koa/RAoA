/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.client.application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;
import android.widget.Toast;

public class ClientApplication extends Application {
	@SuppressLint("SimpleDateFormat")
	@Override
	public void onCreate() {
		super.onCreate();
		final File crashesDir = getDir("crash", MODE_WORLD_READABLE);
		final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(final Thread thread, final Throwable ex) {
				final String filename = "crash-" + dateFormat.format(new Date()) + "-" + thread.getName() + ".txt";
				try {
					final PrintWriter writer = new PrintWriter(new File(crashesDir, filename));
					try {
						ex.printStackTrace(writer);
					} finally {
						writer.close();
					}
				} catch (final FileNotFoundException e) {
					Log.e("CrashWriter", "Cannot write crash to file", e);
				}
				Toast.makeText(ClientApplication.this, "Application crashed", Toast.LENGTH_LONG).show();
			}
		});
	}
}
