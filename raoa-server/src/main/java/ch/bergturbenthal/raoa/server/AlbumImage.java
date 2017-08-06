package ch.bergturbenthal.raoa.server;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.codec.digest.DigestUtils;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

import ch.bergturbenthal.raoa.server.AlbumImageFactory.FileExistsManager;
import ch.bergturbenthal.raoa.server.cache.AlbumManager;
import ch.bergturbenthal.raoa.server.metadata.MetadataWrapper;
import ch.bergturbenthal.raoa.server.metadata.PicasaIniEntryData;
import ch.bergturbenthal.raoa.server.metadata.XmpWrapper;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.thumbnails.ImageThumbnailMaker;
import ch.bergturbenthal.raoa.server.thumbnails.ThumbnailSize;
import ch.bergturbenthal.raoa.server.thumbnails.VideoThumbnailMaker;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Slf4j
public class AlbumImage {

	private static interface XmpRunnable {
		void run(final XmpWrapper xmp);
	}

	private static final Integer STAR_RATING = Integer.valueOf(5);

	/**
	 * Gets image dimensions for given file
	 *
	 * @param imgFile
	 *          image file
	 * @return dimensions of image
	 * @throws IOException
	 *           if the file is not a known image
	 */
	public static Optional<Dimension> getImageDimension(final File imgFile) throws IOException {
		final int pos = imgFile.getName().lastIndexOf(".");
		if (pos == -1) {
			throw new IOException("No extension for file: " + imgFile.getAbsolutePath());
		}
		final String suffix = imgFile.getName().substring(pos + 1);
		final Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);
		while (iter.hasNext()) {
			final ImageReader reader = iter.next();
			try {
				final ImageInputStream stream = new FileImageInputStream(imgFile);
				reader.setInput(stream);
				final int width = reader.getWidth(reader.getMinIndex());
				final int height = reader.getHeight(reader.getMinIndex());
				return Optional.of(new Dimension(width, height));
			} catch (final IOException e) {
				log.warn("Error reading: " + imgFile.getAbsolutePath(), e);
			} finally {
				reader.dispose();
			}
		}

		return Optional.empty();
	}

	private final AlbumManager albumManager;

	private final File cacheDir;

	private final Object entryDataLock = new Object();

	private final File file;

	private final FileExistsManager fileExistsManager;

	private final ImageThumbnailMaker imageThumbnailMaker;

	@Getter
	private final Date lastModified;
	private final Semaphore limitConcurrentScaleSemaphore;

	private final Mono<Long> totalSize;

	private final VideoThumbnailMaker videoThumbnailMaker;

	public AlbumImage(final File file, final File cacheDir, final Date lastModified, final AlbumManager cacheManager, final Semaphore limitConcurrentScaleSemaphore,
										final ImageThumbnailMaker imageThumbnailMaker, final VideoThumbnailMaker videoThumbnailMaker, final FileExistsManager fileExistsManager) {
		this.file = file;
		this.cacheDir = cacheDir;
		this.lastModified = lastModified;
		this.albumManager = cacheManager;
		this.limitConcurrentScaleSemaphore = limitConcurrentScaleSemaphore;
		this.imageThumbnailMaker = imageThumbnailMaker;
		this.videoThumbnailMaker = videoThumbnailMaker;
		this.fileExistsManager = fileExistsManager;
		totalSize = Mono.create((final MonoSink<Long> sink) -> {
			sink.success(calcTotalSize());
		}).cache();
	}

	public void addKeyword(final String keyword) {
		updateXmp(new XmpRunnable() {

			@Override
			public void run(final XmpWrapper xmp) {
				xmp.addKeyword(keyword);
			}
		});
	}

	private long calcTotalSize() {
		long totalSize = file.length();
		totalSize += Stream.of(ThumbnailSize.values()).map(s -> makeCachedFile(s)).distinct().filter(f -> exists(f)).mapToLong(f -> f.length()).sum();
		final File xmpSideFile = getXmpSideFile();
		if (exists(xmpSideFile)) {
			totalSize += xmpSideFile.length();
		}
		return totalSize;
	}

	public Date captureDate() {
		return getAlbumEntryData().estimateCreationDate();
	}

	private boolean exists(final File file) {
		return fileExistsManager.exists(file);
	}

	public AlbumEntryData getAlbumEntryData() {
		synchronized (entryDataLock) {

			final Date lastModifiedMetadata = getMetadataLastModifiedTime();
			{
				final AlbumEntryData loadedMetaData = albumManager.getCachedData();
				if (loadedMetaData != null && Objects.equals(loadedMetaData.getLastModifiedMetadata(), lastModifiedMetadata)) {
					return loadedMetaData;
				}
			}

			final AlbumEntryData loadedMetaData = new AlbumEntryData();
			loadedMetaData.setLastModifiedMetadata(lastModifiedMetadata);
			final Metadata metadata = getExifMetadata();
			if (metadata != null) {
				new MetadataWrapper(metadata).fill(loadedMetaData);
			}
			final PicasaIniEntryData picasaData = albumManager.getPicasaData();
			if (picasaData != null) {
				if (loadedMetaData.getRating() == null && picasaData.isStar()) {
					loadedMetaData.setRating(STAR_RATING);
				}
				loadedMetaData.getKeywords().addAll(picasaData.getKeywords());
				if (loadedMetaData.getCaption() == null) {
					loadedMetaData.setCaption(picasaData.getCaption());
				}
			}
			try {
				loadedMetaData.setOriginalDimension(getImageDimension(file).map(d -> loadedMetaData.getOrientation().map(o -> o.isSwapDimensions()).orElse(Boolean.FALSE)
						? new Dimension((int) d.getHeight(), (int) d.getWidth()) : d));
			} catch (final IOException e1) {
				log.warn("Cannot load dimension of " + file, e1);
			}
			final File xmpSideFile = getXmpSideFile();
			if (exists(xmpSideFile)) {
				try {
					{
						@Cleanup
						final FileInputStream fis = new FileInputStream(xmpSideFile);
						final XmpWrapper xmp = new XmpWrapper(XMPMetaFactory.parse(fis));
						final Integer rating = xmp.readRating();
						if (rating != null) {
							loadedMetaData.setRating(rating);
						}
						if (loadedMetaData.getKeywords() == null) {
							loadedMetaData.setKeywords(new LinkedHashSet<String>());
						}
						loadedMetaData.getKeywords().addAll(xmp.readKeywords());
						final String description = xmp.readDescription();
						if (description != null) {
							loadedMetaData.setCaption(description);
						}
					}
					{
						@Cleanup
						final FileInputStream fis = new FileInputStream(xmpSideFile);
						loadedMetaData.setEditableMetadataHash(DigestUtils.shaHex(fis));
					}
				} catch (final IOException e) {
					log.error("Cannot read XMP-Sidefile: " + xmpSideFile, e);
				} catch (final XMPException e) {
					log.error("Cannot read XMP-Sidefile: " + xmpSideFile, e);
				}
			} else {
				loadedMetaData.setEditableMetadataHash("original-data");
			}

			albumManager.updateCache(loadedMetaData);
			return loadedMetaData;
		}
	}

	public long getAllFilesSize() {
		return totalSize.block();
	}

	private Metadata getExifMetadata() {
		try {
			Metadata exifMetadata;
			final long startTime = System.currentTimeMillis();
			exifMetadata = ImageMetadataReader.readMetadata(file);
			final long endTime = System.currentTimeMillis();
			log.info("Metadata-Read: " + (endTime - startTime) + " ms");
			return exifMetadata;
		} catch (final IOException e) {
			log.warn("Cannot reade metadata from " + file, e);
		} catch (final ImageProcessingException e) {
			log.warn("Cannot reade metadata from " + file, e);
		}
		return null;
	}

	/**
	 * @return
	 */
	private Date getMetadataLastModifiedTime() {
		final File xmpSideFile = getXmpSideFile();
		if (!exists(xmpSideFile)) {
			return null;
		}
		return new Date(xmpSideFile.lastModified());
	}

	public String getName() {
		return file.getName();
	}

	public File getOriginalFile() {
		return file;
	}

	public long getOriginalFileSize() {
		return file.length();
	}

	public File getThumbnail(final ThumbnailSize size) {
		return getThumbnail(size, false);
	}

	public File getThumbnail(final ThumbnailSize size, final boolean onlyFromCache) {
		try {
			final File cachedFile = makeCachedFile(size);
			final long originalLastModified = file.lastModified();
			if (exists(cachedFile) && cachedFile.lastModified() == originalLastModified) {
				albumManager.clearThumbnailException(getName());
				return cachedFile;
			}
			if (onlyFromCache) {
				return null;
			}
			synchronized (this) {
				if (exists(cachedFile) && cachedFile.lastModified() == originalLastModified) {
					albumManager.clearThumbnailException(getName());
					return cachedFile;
				}
				limitConcurrentScaleSemaphore.acquire();
				try {
					if (isVideo()) {
						videoThumbnailMaker.makeVideoThumbnail(file, cachedFile, cacheDir);
					} else {
						imageThumbnailMaker.makeImageThumbnail(file, cachedFile, cacheDir, size);
					}
					fileExistsManager.reCheck(cachedFile);
					cachedFile.setLastModified(originalLastModified);
				} finally {
					limitConcurrentScaleSemaphore.release();
				}
			}
			albumManager.clearThumbnailException(getName());
			return cachedFile;
		} catch (final Exception e) {
			albumManager.recordThumbnailException(getName(), e);
			return null;
		}
	}

	public File getXmpSideFile() {
		final String name = file.getName();
		final int lastPt = name.lastIndexOf('.');
		final String baseName;
		if (lastPt > 0) {
			baseName = name.substring(0, lastPt);
		} else {
			baseName = name;
		}
		final File shortFilename = new File(file.getParentFile(), baseName + ".xmp");
		if (exists(shortFilename)) {
			return shortFilename;
		}
		final File longFilename = new File(file.getParentFile(), name + ".xmp");
		if (exists(longFilename)) {
			return longFilename;
		}
		return longFilename;
	}

	/**
	 * returns true if the image is a video
	 *
	 * @return
	 */
	public boolean isVideo() {
		final String lowerFilename = file.getName().toLowerCase();
		return lowerFilename.endsWith(".mkv") || lowerFilename.endsWith(".mp4");
	}

	public Date lastModified() {
		if (getThumbnail(ThumbnailSize.BIG, true) == null) {
			return new Date(lastModified.getTime() - 1);
		}
		return lastModified;
	}

	private File makeCachedFile(final ThumbnailSize size) {
		final String name = file.getName();
		if (isVideo()) {
			return new File(cacheDir, name.substring(0, name.length() - 4) + ".mp4");
		}
		final String prefixedName = size == ThumbnailSize.BIG ? name : "small-" + name;
		return new File(cacheDir, prefixedName);
	}

	public void removeKeyword(final String keyword) {
		updateXmp(new XmpRunnable() {

			@Override
			public void run(final XmpWrapper xmp) {
				xmp.removeKeyword(keyword);
			}
		});
	}

	public void setCaption(final String caption) {
		updateXmp(new XmpRunnable() {

			@Override
			public void run(final XmpWrapper xmp) {
				xmp.updateDescription(caption);
			}
		});
	}

	public void setRating(final Integer rating) {
		updateXmp(new XmpRunnable() {
			@Override
			public void run(final XmpWrapper xmp) {
				xmp.setRating(rating);
			}
		});
	}

	@Override
	public String toString() {
		return "AlbumImage [file=" + file.getName() + "]";
	}

	private void updateXmp(final XmpRunnable runnable) {
		final File xmpSideFile = getXmpSideFile();
		final XMPMeta xmpMeta;
		if (exists(xmpSideFile)) {
			try {
				@Cleanup
				final FileInputStream fis = new FileInputStream(xmpSideFile);
				xmpMeta = XMPMetaFactory.parse(fis);
			} catch (final IOException e) {
				throw new RuntimeException("Cannot parse " + xmpSideFile, e);
			} catch (final XMPException e) {
				throw new RuntimeException("Cannot parse " + xmpSideFile, e);
			}
		} else {
			xmpMeta = XMPMetaFactory.create();
		}
		final XmpWrapper xmp = new XmpWrapper(xmpMeta);
		runnable.run(xmp);
		final File tempSideFile = new File(xmpSideFile.getParent(), xmpSideFile.getName() + "-tmp");
		try {
			{
				@Cleanup
				final FileOutputStream fos = new FileOutputStream(tempSideFile);
				XMPMetaFactory.serialize(xmpMeta, fos);
			}
			tempSideFile.renameTo(xmpSideFile);
			fileExistsManager.reCheck(xmpSideFile);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot write " + tempSideFile, e);
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot write " + tempSideFile, e);
		}

	}

}
