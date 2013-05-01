package ch.bergturbenthal.image.client.resolver;

import java.io.File;
import java.io.FileFilter;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;

public class SingleMediaScanner implements MediaScannerConnectionClient {

	private final File mFile;
	private final MediaScannerConnection mMs;

	public SingleMediaScanner(final Context context, final File f) {
		mFile = f;
		mMs = new MediaScannerConnection(context, this);
		mMs.connect();
	}

	@Override
	public void onMediaScannerConnected() {
		if (mFile.isDirectory()) {
			scanDirectory(mFile);
		} else {
			mMs.scanFile(mFile.getAbsolutePath(), "image/jpeg");
		}
	}

	@Override
	public void onScanCompleted(final String path, final Uri uri) {
		mMs.disconnect();
	}

	private void scanDirectory(final File directory) {
		for (final File file : directory.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File pathname) {
				return pathname.isFile() && pathname.canRead() && pathname.getName().endsWith(".jpg");
			}
		})) {
			mMs.scanFile(file.getAbsolutePath(), "image/jpeg");
		}
		for (final File subdir : directory.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File pathname) {
				return pathname.isDirectory();
			}
		})) {
			scanDirectory(subdir);
		}
	}

}
