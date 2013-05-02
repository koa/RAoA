package ch.bergturbenthal.raoa.server.thumbnails;

import java.io.File;

public interface VideoThumbnailMaker {
  boolean makeVideoThumbnail(final File originalFile, final File thumbnailFile, final File tempDir);
}
