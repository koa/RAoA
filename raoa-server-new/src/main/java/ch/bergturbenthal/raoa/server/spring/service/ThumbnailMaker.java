package ch.bergturbenthal.raoa.server.spring.service;

import java.io.File;
import java.util.concurrent.ExecutorService;

public interface ThumbnailMaker {
	boolean makeThumbnail(final File originalFile, final File thumbnailFile, final File tempDir);

	boolean canMakeThumbnail(final String filename);

	ExecutorService createExecutorservice();

}
