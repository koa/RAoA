/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TODO: add type comment.
 * 
 */
public class IOUtil {
	public static void copyStream(final InputStream is, final OutputStream os) {
		try {
			final byte[] buffer = new byte[8192];
			while (true) {
				final int read = is.read(buffer);
				if (read < 0) {
					break;
				}
				os.write(buffer, 0, read);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] readStream(final InputStream is) {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		copyStream(is, os);
		return os.toByteArray();
	}
}
