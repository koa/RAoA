package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.bergturbenthal.image.server.cache.AlbumEntryCacheManager;
import ch.bergturbenthal.image.server.model.AlbumEntryData;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class AlbumImage {

  private static Map<File, Object> imageLocks = new WeakHashMap<File, Object>();
  private static Map<File, SoftReference<AlbumImage>> loadedImages = new HashMap<File, SoftReference<AlbumImage>>();
  private final static Logger logger = LoggerFactory.getLogger(AlbumImage.class);
  private static final int THUMBNAIL_SIZE = 1600;

  private static Semaphore limitConcurrentScaleSemaphore = new Semaphore(4);

  public static AlbumImage makeImage(final File file, final File cacheDir, final Date lastModified, final AlbumEntryCacheManager cacheManager) {
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

  private final File file;

  private final Date lastModified;

  private final AlbumEntryCacheManager cacheManager;

  public AlbumImage(final File file, final File cacheDir, final Date lastModified, final AlbumEntryCacheManager cacheManager) {
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
            scaleVideoDown(cachedFile);
          else
            scaleImageDown(cachedFile);
        } finally {
          limitConcurrentScaleSemaphore.release();
        }
      }
      return cachedFile;
    } catch (final IOException e) {
      throw new RuntimeException("Cannot make thumbnail of " + file, e);
    } catch (final InterruptedException e) {
      throw new RuntimeException("Cannot make thumbnail of " + file, e);
    } catch (final IM4JavaException e) {
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
      loadedMetaData.setCreationDate(MetadataUtil.readCreateDate(metadata));
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

  private File makeCachedFile() {
    final String name = file.getName();
    if (isVideo()) {
      return new File(cacheDir, name.substring(0, name.length() - 4) + ".mp4");
    }
    return new File(cacheDir, name);
  }

  private void scaleImageDown(final File cachedFile) throws IOException, InterruptedException, IM4JavaException {
    final File tempFile = new File(cachedFile.getParentFile(), cachedFile.getName() + ".tmp.jpg");
    if (tempFile.exists())
      tempFile.delete();
    // logger.debug("Convert " + file);
    final ConvertCmd cmd = new ConvertCmd();
    final File secondStepInputFile;
    final boolean deleteInputFileAfter;
    if (!file.getName().toLowerCase().endsWith("jpg")) {
      secondStepInputFile = new File(cachedFile.getParentFile(), cachedFile.getName() + ".tmp.png");
      if (secondStepInputFile.exists())
        secondStepInputFile.delete();
      final IMOperation primaryOperation = new IMOperation();
      primaryOperation.addImage(file.getAbsolutePath());
      primaryOperation.addImage(secondStepInputFile.getAbsolutePath());
      // logger.debug("Start conversion prepare: " + primaryOperation);
      cmd.run(primaryOperation);
      deleteInputFileAfter = true;
    } else {
      secondStepInputFile = file;
      deleteInputFileAfter = false;
    }
    final IMOperation secondOperation = new IMOperation();
    secondOperation.addImage(secondStepInputFile.getAbsolutePath());
    secondOperation.autoOrient();
    secondOperation.resize(Integer.valueOf(THUMBNAIL_SIZE), Integer.valueOf(THUMBNAIL_SIZE));
    secondOperation.quality(Double.valueOf(70));
    secondOperation.addImage(tempFile.getAbsolutePath());
    // logger.debug("Start conversion: " + secondOperation);
    cmd.run(secondOperation);
    tempFile.renameTo(cachedFile);
    if (deleteInputFileAfter)
      secondStepInputFile.delete();
    // logger.debug("End operation");
  }

  private synchronized void scaleVideoDown(final File cachedFile) {
    // avconv -i file001.mkv -vcodec libx264 -b:v 1024k -profile:v baseline -b:a
    // 24k -vf yadif -vf scale=1280:720 -acodec libvo_aacenc -sn -r 30
    // .servercache/out.mp4
    logger.debug("Start transcode " + file);
    final File tempFile = new File(cachedFile.getParentFile(), cachedFile.getName() + "-tmp.mp4");
    if (tempFile.exists())
      tempFile.delete();
    try {
      final Process process =
                              Runtime.getRuntime().exec(new String[] { "avconv", "-i", file.getAbsolutePath(), "-vcodec", "libx264", "-b:v", "1024k",
                                                                      "-profile:v", "baseline", "-b:a", "24k", "-vf", "yadif", "-vf",
                                                                      "scale=1280:720", "-acodec", "libvo_aacenc", "-sn", "-r", "30",
                                                                      tempFile.getAbsolutePath() });
      final StringBuilder errorMessage = new StringBuilder();
      final InputStreamReader reader = new InputStreamReader(process.getErrorStream());
      final char[] buffer = new char[8192];
      while (true) {
        final int read = reader.read(buffer);
        if (read < 0)
          break;
        errorMessage.append(buffer, 0, read);
      }
      final int resultCode = process.waitFor();
      if (resultCode != 0)
        throw new RuntimeException("Cannot convert video " + file + ": RC-Code: " + resultCode + "\n" + errorMessage.toString());
      tempFile.renameTo(cachedFile);
      logger.debug("End transcode " + file);
    } catch (final IOException e) {
      throw new RuntimeException("Cannot convert video " + file, e);
    } catch (final InterruptedException e) {
      throw new RuntimeException("Cannot convert video " + file, e);
    }
  }

}
