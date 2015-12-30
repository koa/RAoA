package ch.bergturbenthal.raoa.server.spring.service;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface ThumbnailMaker {
	boolean canMakeThumbnail(final String filename);

	boolean makeThumbnail(final File originalFile, final File thumbnailFile, final File tempDir);

	boolean makeThumbnailImage(final File originalFile, final File thumbnailFile, final int thumbnailSize, final File tempDir);

	<T> Future<T> submit(Callable<T> callable);

}
