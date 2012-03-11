package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlbumImage {
  private final static Logger logger = LoggerFactory.getLogger(AlbumImage.class);
  private final static MessageFormat THUMBNAIL_MESSAGE_FORMAT = new MessageFormat("{0}-{1}_{2}.jpg");
  private final static MessageFormat CROP_THUMBNAIL_MESSAGE_FORMAT = new MessageFormat("{0}-{1}_{2}c.jpg");
  private final File file;
  private final File cacheDir;

  public AlbumImage(final File file, final File cacheDir) {
    this.file = file;
    this.cacheDir = cacheDir;
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
