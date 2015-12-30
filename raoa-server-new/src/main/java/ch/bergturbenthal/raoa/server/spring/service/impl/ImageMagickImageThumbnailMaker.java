package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.ImageCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;

@Service
public class ImageMagickImageThumbnailMaker implements ThumbnailMaker {
	private static final Logger log = LoggerFactory.getLogger(ImageMagickImageThumbnailMaker.class);
	private ImageCommand cmd = null;
	private final ExecutorService executorService = new ThreadPoolExecutor(	0,
																																					Runtime.getRuntime().availableProcessors(),
																																					30,
																																					TimeUnit.SECONDS,
																																					new ArrayBlockingQueue<Runnable>(20),
																																					new CustomizableThreadFactory("Image-Magik pool-"),
																																					new ThreadPoolExecutor.CallerRunsPolicy());
	private String gmBinary = null;
	private String imConvertBinary = null;

	private int thumbnailSize = 1600;

	@Override
	public boolean canMakeThumbnail(final String filename) {
		final String lowerFilename = filename.toLowerCase();
		return lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || lowerFilename.endsWith(".png") || lowerFilename.endsWith(".nef");
	}

	private boolean checkCommand(final ImageCommand cmd) {
		final IMOperation imOperation = new IMOperation();
		imOperation.version();
		try {
			cmd.run(imOperation);
			return true;
		} catch (final IOException e) {
			log.info("Cannot execute command while checking calling of ImageMagick", e);
			return false;
		} catch (final InterruptedException e) {
			log.info("Cannot execute command while checking calling of ImageMagick", e);
			return false;
		} catch (final IM4JavaException e) {
			log.info("Cannot execute command while checking calling of ImageMagick", e);
			return false;
		}
	}

	@PostConstruct
	public void init() {
		final Collection<ImageCommand> cmdCandidates = new ArrayList<>();

		if (imConvertBinary != null) {
			cmdCandidates.add(new ImageCommand(imConvertBinary));
		}
		if (gmBinary != null) {
			cmdCandidates.add(new ImageCommand(gmBinary, "convert"));
		}
		cmdCandidates.addAll(Arrays.asList(new ConvertCmd(), new ConvertCmd(false), new ConvertCmd(true)));

		for (final ImageCommand imageCommand : cmdCandidates) {
			if (checkCommand(imageCommand)) {
				cmd = imageCommand;
				break;
			}
		}
		if (cmd == null) {
			throw new RuntimeException("No compatible Image-Magick found");
		}
	}

	@Override
	public boolean makeThumbnail(final File originalFile, final File thumbnailFile, final File tempDir) {
		return makeThumbnailImage(originalFile, thumbnailFile, thumbnailSize, tempDir);
	}

	@Override
	public boolean makeThumbnailImage(final File originalFile, final File thumbnailFile, final int thumbnailSize, final File tempDir) {
		if (cmd == null) {
			return false;
		}
		boolean deleteInputFileAfter = false;
		final File tempFile = new File(tempDir, originalFile.getName() + ".tmp.jpg");

		final File file = originalFile;

		if (tempFile.exists()) {
			tempFile.delete();
		}
		// logger.debug("Convert " + file);
		final ConvertCmd cmd = new ConvertCmd();
		File secondStepInputFile = null;
		try {
			if (!file.getName().toLowerCase().endsWith("jpg")) {
				secondStepInputFile = new File(tempDir, thumbnailFile.getName() + ".tmp.png");
				if (secondStepInputFile.exists()) {
					secondStepInputFile.delete();
				}
				final IMOperation primaryOperation = new IMOperation();
				primaryOperation.addImage(file.getAbsolutePath());
				primaryOperation.addImage(secondStepInputFile.getAbsolutePath());
				// logger.debug("Start conversion prepare: " + primaryOperation);
				deleteInputFileAfter = true;
				cmd.run(primaryOperation);
			} else {
				secondStepInputFile = file;
				deleteInputFileAfter = false;
			}
			final IMOperation secondOperation = new IMOperation();
			secondOperation.addImage(secondStepInputFile.getAbsolutePath());
			secondOperation.autoOrient();
			secondOperation.resize(Integer.valueOf(thumbnailSize), Integer.valueOf(thumbnailSize));
			secondOperation.quality(Double.valueOf(70));
			secondOperation.addImage(tempFile.getAbsolutePath());
			// logger.debug("Start conversion: " + secondOperation);
			cmd.run(secondOperation);
			tempFile.renameTo(thumbnailFile);
			return true;
		} catch (final Exception e) {
			throw new RuntimeException("Cannot scale image " + originalFile, e);
		} finally {
			if (deleteInputFileAfter && secondStepInputFile != null) {
				secondStepInputFile.delete();
			}
		}
	}

	public void setGmBinary(final String gmBinary) {
		this.gmBinary = gmBinary;
	}

	public void setImConvertBinary(final String imConvertBinary) {
		this.imConvertBinary = imConvertBinary;
	}

	public void setThumbnailSize(final int thumbnailSize) {
		this.thumbnailSize = thumbnailSize;
	}

	@Override
	public <T> Future<T> submit(final Callable<T> callable) {
		return executorService.submit(callable);
	}

}
