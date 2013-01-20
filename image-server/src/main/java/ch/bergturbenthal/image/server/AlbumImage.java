package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import lombok.Cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ch.bergturbenthal.image.server.cache.AlbumEntryCacheManager;
import ch.bergturbenthal.image.server.metadata.MetadataWrapper;
import ch.bergturbenthal.image.server.metadata.PicasaIniData;
import ch.bergturbenthal.image.server.metadata.XmpWrapper;
import ch.bergturbenthal.image.server.model.AlbumEntryData;
import ch.bergturbenthal.image.server.thumbnails.ImageThumbnailMaker;
import ch.bergturbenthal.image.server.thumbnails.VideoThumbnailMaker;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMetaFactory;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class AlbumImage {

  private static final Integer STAR_RATING = Integer.valueOf(5);
  private static Map<File, Object> imageLocks = new WeakHashMap<File, Object>();
  private static Semaphore limitConcurrentScaleSemaphore = new Semaphore(4);
  private static Map<File, SoftReference<AlbumImage>> loadedImages = new HashMap<File, SoftReference<AlbumImage>>();
  private final static Logger logger = LoggerFactory.getLogger(AlbumImage.class);

  public static AlbumImage createImage(final File file, final File cacheDir, final Date lastModified, final AlbumEntryCacheManager cacheManager) {
    synchronized (lockFor(file)) {
      final SoftReference<AlbumImage> softReference = loadedImages.get(file);
      if (softReference != null) {
        final AlbumImage cachedImage = softReference.get();
        if (cachedImage != null && cachedImage.lastModified.equals(lastModified))
          return cachedImage;
      }
      final AlbumImage newImage = new AlbumImage(file, cacheDir, lastModified, cacheManager);
      loadedImages.put(file, new SoftReference<AlbumImage>(newImage));
      return newImage;
    }
  }

  private static synchronized Object lockFor(final File file) {
    final Object existingLock = imageLocks.get(file);
    if (existingLock != null)
      return existingLock;
    final Object newLock = new Object();
    imageLocks.put(file, newLock);
    return newLock;
  }

  private final File cacheDir;

  private final AlbumEntryCacheManager cacheManager;

  private final File file;

  @Autowired
  private ImageThumbnailMaker imageThumbnailMaker;

  private final Date lastModified;

  @Autowired
  private VideoThumbnailMaker videoThumbnailMaker;

  private AlbumImage(final File file, final File cacheDir, final Date lastModified, final AlbumEntryCacheManager cacheManager) {
    this.file = file;
    this.cacheDir = cacheDir;
    this.lastModified = lastModified;
    this.cacheManager = cacheManager;
  }

  public Date captureDate() {
    return getAlbumEntryData().getCreationDate();
  }

  public String getName() {
    return file.getName();
  }

  public long getOriginalFileSize() {
    return file.length();
  }

  public File getThumbnail() {
    try {
      final File cachedFile = makeCachedFile();
      if (cachedFile.exists() && cachedFile.lastModified() > file.lastModified())
        return cachedFile;
      synchronized (this) {
        if (cachedFile.exists() && cachedFile.lastModified() >= file.lastModified())
          return cachedFile;
        limitConcurrentScaleSemaphore.acquire();
        try {
          if (isVideo())
            videoThumbnailMaker.makeVideoThumbnail(file, cachedFile, cacheDir);
          else
            imageThumbnailMaker.makeImageThumbnail(file, cachedFile, cacheDir);
        } finally {
          limitConcurrentScaleSemaphore.release();
        }
      }
      return cachedFile;
    } catch (final Exception e) {
      throw new RuntimeException("Cannot make thumbnail of " + file, e);
    }
  }

  /**
   * returns true if the image is a video
   * 
   * @return
   */
  public boolean isVideo() {
    return file.getName().toLowerCase().endsWith(".mkv");
  }

  public Date lastModified() {
    return lastModified;
  }

  @Override
  public String toString() {
    return "AlbumImage [file=" + file.getName() + "]";
  }

  private synchronized AlbumEntryData getAlbumEntryData() {
    AlbumEntryData loadedMetaData = cacheManager.getCachedData();
    if (loadedMetaData != null)
      return loadedMetaData;

    loadedMetaData = new AlbumEntryData();
    final Metadata metadata = getExifMetadata();
    if (metadata != null) {
      new MetadataWrapper(metadata).fill(loadedMetaData);
    }
    final PicasaIniData picasaData = cacheManager.getPicasaData();
    if (picasaData != null) {
      if (loadedMetaData.getRating() == null && picasaData.isStar())
        loadedMetaData.setRating(STAR_RATING);
      loadedMetaData.getKeywords().addAll(picasaData.getKeywords());
      if (loadedMetaData.getCaption() == null)
        loadedMetaData.setCaption(picasaData.getCaption());
    }
    final File xmpSideFile = getXmpSideFile();
    if (xmpSideFile.exists()) {
      try {
        @Cleanup
        final FileInputStream fis = new FileInputStream(xmpSideFile);
        final XmpWrapper xmp = new XmpWrapper(XMPMetaFactory.parse(fis));
        final Integer rating = xmp.readRating();
        if (rating != null)
          loadedMetaData.setRating(rating);
        loadedMetaData.getKeywords().addAll(xmp.readKeywords());
        final String description = xmp.readDescription();
        if (description != null)
          loadedMetaData.setCaption(description);
      } catch (final IOException e) {
        logger.error("Cannot read XMP-Sidefile: " + xmpSideFile, e);
      } catch (final XMPException e) {
        logger.error("Cannot read XMP-Sidefile: " + xmpSideFile, e);
      }
    }

    cacheManager.updateCache(loadedMetaData);
    return loadedMetaData;
  }

  private Metadata getExifMetadata() {
    try {
      Metadata exifMetadata;
      final long startTime = System.currentTimeMillis();
      exifMetadata = ImageMetadataReader.readMetadata(file);
      final long endTime = System.currentTimeMillis();
      logger.info("Metadata-Read: " + (endTime - startTime) + " ms");
      return exifMetadata;
    } catch (final IOException e) {
      logger.warn("Cannot reade metadata from " + file, e);
    } catch (final ImageProcessingException e) {
      logger.warn("Cannot reade metadata from " + file, e);
    }
    return null;
  }

  private File getXmpSideFile() {
    return new File(file.getParent(), file.getName() + ".xmp");
  }

  private File makeCachedFile() {
    final String name = file.getName();
    if (isVideo()) {
      return new File(cacheDir, name.substring(0, name.length() - 4) + ".mp4");
    }
    return new File(cacheDir, name);
  }

}
