package ch.bergturbenthal.raoa.server;

import java.io.File;
import java.util.Date;

import ch.bergturbenthal.raoa.server.cache.AlbumManager;

public interface AlbumImageFactory {
	public static interface FileExistsManager {
		boolean exists(File file);

		void reCheck(File file);
	}

	AlbumImage createImage(final File file, final File cacheDir, final Date lastModified, final AlbumManager cacheManager, FileExistsManager existFunction);
}
