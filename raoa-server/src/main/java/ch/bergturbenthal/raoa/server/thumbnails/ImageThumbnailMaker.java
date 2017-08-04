package ch.bergturbenthal.raoa.server.thumbnails;

import java.io.File;

public interface ImageThumbnailMaker {
	boolean makeImageThumbnail(final File originalFile, final File thumbnailFile, final File tempDir, ThumbnailSize size);
}
