package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifDirectory;
import com.drew.metadata.exif.GpsDirectory;

public class AlbumImage {
  private static class TagId {
    private final Class<? extends Directory> directory;
    private final int tagId;

    public TagId(final Class<? extends Directory> directory, final int tagId) {
      this.directory = directory;
      this.tagId = tagId;
    }

  }

  private static Map<File, Object> imageLocks = new WeakHashMap<File, Object>();
  private static Map<File, SoftReference<AlbumImage>> loadedImages = new HashMap<File, SoftReference<AlbumImage>>();
  private final static Logger logger = LoggerFactory.getLogger(AlbumImage.class);
  private static final int THUMBNAIL_SIZE = 1600;

  private static ObjectMapper objectMapper = new ObjectMapper();

  public static AlbumImage makeImage(final File file, final File cacheDir) {
    synchronized (lockFor(file)) {
      final SoftReference<AlbumImage> softReference = loadedImages.get(file);
      if (softReference != null) {
        final AlbumImage cachedImage = softReference.get();
        if (cachedImage != null)
          return cachedImage;
      }
      final AlbumImage newImage = new AlbumImage(file, cacheDir);
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

  private Date captureDate = null;

  private final File file;

  private Metadata metadata = null;

  public AlbumImage(final File file, final File cacheDir) {
    this.file = file;
    this.cacheDir = cacheDir;
  }

  public synchronized Date captureDate() {
    if (captureDate == null)
      captureDate = readCaptureDateFromMetadata();

    return captureDate;
  }

  public String getName() {
    return file.getName();
  }

  public File getThumbnail() {
    try {
      final File cachedFile = makeCachedFile();
      if (cachedFile.exists() && cachedFile.lastModified() > file.lastModified())
        return cachedFile;
      synchronized (this) {
        if (cachedFile.exists() && cachedFile.lastModified() >= file.lastModified())
          return cachedFile;
        if (isVideo())
          scaleVideoDown(cachedFile);
        else
          scaleImageDown(cachedFile);
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
    return new Date(file.lastModified());
  }

  public long readSize() {
    return file.length();
  }

  @Override
  public String toString() {
    return "AlbumImage [file=" + file.getName() + "]";
  }

  private synchronized Metadata getMetadata() {
    if (metadata != null)
      return metadata;
    final File metdataCacheFile = makeMetdataCacheFile();
    if (metdataCacheFile.exists() && metdataCacheFile.lastModified() >= file.lastModified()) {
      try {
        final ObjectInputStream is = new ObjectInputStream(new FileInputStream(metdataCacheFile));
        try {
          metadata = (Metadata) is.readObject();
        } finally {
          is.close();
        }
      } catch (final ClassNotFoundException e) {
        logger.warn("Cannot read metadata-cache " + metdataCacheFile, e);
      } catch (final IOException e) {
        logger.warn("Cannot read metadata-cache " + metdataCacheFile, e);
      }
      if (metadata != null)
        return metadata;
    }
    try {
      // final long startTime = System.currentTimeMillis();
      metadata = ImageMetadataReader.readMetadata(file);
      // final long endTime = System.currentTimeMillis();
      // logger.info("Metadata-Read: " + (endTime - startTime) + " ms");
      final ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(metdataCacheFile));
      try {
        objectOutputStream.writeObject(metadata);
      } finally {
        objectOutputStream.close();
      }
    } catch (final ImageProcessingException e) {
      throw new RuntimeException("Cannot read metadata from " + file, e);
    } catch (final IOException e) {
      logger.warn("Cannot save metadata-cache " + metdataCacheFile, e);
    }
    return metadata;
  }

  private File makeCachedFile() {
    final String name = file.getName();
    if (isVideo()) {
      return new File(cacheDir, name.substring(0, name.length() - 4) + ".mp4");
    }
    return new File(cacheDir, name);
  }

  private File makeMetdataCacheFile() {
    final String name = file.getName();
    return new File(cacheDir, name + ".metadata");
  }

  private Date readCaptureDateFromMetadata() {
    if (isVideo())
      return null;
    final Date gpsDate = readGpsDate();
    if (gpsDate != null)
      return gpsDate;
    for (final TagId index : Arrays.asList(new TagId(ExifDirectory.class, ExifDirectory.TAG_DATETIME_ORIGINAL), new TagId(ExifDirectory.class,
                                                                                                                          ExifDirectory.TAG_DATETIME))) {
      final Date date = readDate(index.directory, index.tagId);
      if (date != null)
        return date;
    }
    return null;
  }

  private Date readDate(final Class<? extends Directory> directory, final int tag) {
    try {
      final Metadata metadata = getMetadata();
      if (metadata.containsDirectory(directory)) {
        final Directory directory2 = metadata.getDirectory(directory);
        if (directory2.containsTag(tag))
          try {
            return directory2.getDate(tag);
          } catch (final MetadataException e) {
            throw new RuntimeException("Cannot read " + directory.getName() + ":" + directory2.getDescription(tag) + " from " + file, e);
          }
      }
      return null;
    } catch (final MetadataException e) {
      throw new RuntimeException("Cannot read " + directory.getName() + ":" + tag + " from " + file, e);
    }
  }

  private Date readGpsDate() {
    try {
      final Metadata metadata = getMetadata();
      if (!metadata.containsDirectory(GpsDirectory.class))
        return null;
      final Directory directory = metadata.getDirectory(GpsDirectory.class);
      if (!directory.containsTag(GpsDirectory.TAG_GPS_TIME_STAMP))
        return null;
      final int[] time = directory.getIntArray(7);
      final String date = directory.getString(29);
      final Object[] values = new MessageFormat("{0,number}:{1,number}:{2,number}").parse(date);
      final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      calendar.set(((Number) values[0]).intValue(), ((Number) values[1]).intValue() - 1, ((Number) values[2]).intValue(), time[0], time[1], time[2]);
      return calendar.getTime();
    } catch (final MetadataException e) {
      throw new RuntimeException("Cannot read Gps-Date from " + file, e);
    } catch (final ParseException e) {
      throw new RuntimeException("Cannot read Gps-Date from " + file, e);
    }
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
