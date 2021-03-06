package ch.bergturbenthal.raoa.server.thumbnails;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.ImageCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageMagickImageThumbnailMaker implements ImageThumbnailMaker {
	private static final Logger log = LoggerFactory.getLogger(ImageMagickImageThumbnailMaker.class);
	private ImageCommand cmd = null;
	private String gmBinary = null;
	private String imConvertBinary = null;
	private final Map<ThumbnailSize, Integer> sizeMap = Collections.synchronizedMap(new HashMap<ThumbnailSize, Integer>());
	{
		sizeMap.put(ThumbnailSize.BIG, 1600);
		sizeMap.put(ThumbnailSize.SMALL, 200);
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

	private Integer lengthOf(final ThumbnailSize size) {
		return sizeMap.get(size);
	}

	@Override
	public boolean makeImageThumbnail(final File originalFile, final File thumbnailFile, final File tempDir, final ThumbnailSize size) {
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
				secondStepInputFile = new File(tempDir, thumbnailFile.getName() + "-" + size + ".tmp.png");
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
			final Integer sizeNr = lengthOf(size);
			secondOperation.resize(sizeNr, sizeNr);
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

	public void setThumbnailSize(final ThumbnailSize size, final int thumbnailSize) {
		sizeMap.put(size, thumbnailSize);
	}

}
