package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

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

  private final static Logger logger = LoggerFactory.getLogger(AlbumImage.class);
  private final static MessageFormat THUMBNAIL_MESSAGE_FORMAT = new MessageFormat("{0}-{1}_{2}.jpg");
  private final static MessageFormat CROP_THUMBNAIL_MESSAGE_FORMAT = new MessageFormat("{0}-{1}_{2}c.jpg");
  private final File file;
  private final File cacheDir;
  private Metadata metadata;
  private final Date captureDate;

  public AlbumImage(final File file, final File cacheDir) {
    this.file = file;
    this.cacheDir = cacheDir;
    try {
      metadata = ImageMetadataReader.readMetadata(file);
    } catch (final ImageProcessingException e) {
      throw new RuntimeException("Cannot read metadata from " + file, e);
    }
    captureDate = readCaptureDateFromMetadata();
  }

  public Date captureDate() {
    return captureDate;
  }

  public String getName() {
    return file.getName();
  }

  public File getThumbnail(final int width, final int height, final boolean crop) {
    try {
      final File cachedFile = new File(cacheDir, makeCachedFilename(width, height, crop));
      if (cachedFile.exists() && cachedFile.lastModified() > file.lastModified())
        return cachedFile;
      synchronized (this) {
        if (cachedFile.exists() && cachedFile.lastModified() > file.lastModified())
          return cachedFile;
        scaleImageDown(width, height, crop, cachedFile);
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

  public long readSize() {
    return file.length();
  }

  @Override
  public String toString() {
    return "AlbumImage [file=" + file.getName() + "]";
  }

  private String makeCachedFilename(final int width, final int height, final boolean crop) {
    final MessageFormat filenameFormat;
    if (crop)
      filenameFormat = CROP_THUMBNAIL_MESSAGE_FORMAT;
    else
      filenameFormat = THUMBNAIL_MESSAGE_FORMAT;
    final String cacheFileName;
    synchronized (filenameFormat) {
      cacheFileName = filenameFormat.format(new Object[] { file.getName(), width, height });
    }
    return cacheFileName;
  }

  private Date readCaptureDateFromMetadata() {
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
      if (!metadata.containsDirectory(GpsDirectory.class))
        return null;
      final Directory directory = metadata.getDirectory(GpsDirectory.class);
      if (!directory.containsTag(GpsDirectory.TAG_GPS_TIME_STAMP))
        return null;
      final int[] time = directory.getIntArray(7);
      final String date = directory.getString(29);
      final Object[] values = new MessageFormat("{0,number}:{1,number}:{2,number}").parse(date);
      final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      calendar.set(((Number) values[0]).intValue(), ((Number) values[1]).intValue(), ((Number) values[2]).intValue(), time[0], time[1], time[2]);
      return calendar.getTime();
    } catch (final MetadataException e) {
      throw new RuntimeException("Cannot read Gps-Date from " + file, e);
    } catch (final ParseException e) {
      throw new RuntimeException("Cannot read Gps-Date from " + file, e);
    }
  }

  private void scaleImageDown(final int width, final int height, final boolean crop, final File cachedFile) throws IOException, InterruptedException,
                                                                                                           IM4JavaException {
    logger.debug("Start convert " + file);
    final ConvertCmd cmd = new ConvertCmd();
    final IMOperation operation = new IMOperation();
    operation.addImage(file.getAbsolutePath());
    if (crop) {
      operation.resize(Integer.valueOf(width), Integer.valueOf(height), "^");
      operation.gravity("center");
      operation.extent(Integer.valueOf(width), Integer.valueOf(height));
    } else
      operation.resize(Integer.valueOf(width), Integer.valueOf(height));
    operation.addImage(cachedFile.getAbsolutePath());
    logger.debug("Start operation");
    cmd.run(operation);
    logger.debug("End operation");
  }

}
