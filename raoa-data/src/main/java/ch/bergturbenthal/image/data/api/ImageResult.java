package ch.bergturbenthal.image.data.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ImageResult {
  public static interface StreamSource {
    InputStream getInputStream() throws IOException;
  }

  private final Date lastModified;

  private final StreamSource dataStream;

  private final Date created;
  private final boolean modified;
  private final String mimeType;

  public static ImageResult makeModifiedResult(final Date lastModified, final Date created, final StreamSource dataStream, final String mimeType) {
    return new ImageResult(lastModified, created, dataStream, mimeType, true);
  }

  public static ImageResult makeNotModifiedResult() {
    return new ImageResult(null, null, null, null, false);
  }

  private ImageResult(final Date lastModified, final Date created, final StreamSource dataStream, final String mimeType, final boolean modified) {
    super();
    this.lastModified = lastModified;
    this.created = created;
    this.dataStream = dataStream;
    this.mimeType = mimeType;
    this.modified = modified;
  }

  public Date getCreated() {
    return created;
  }

  public InputStream getDataStream() {
    try {
      return dataStream.getInputStream();
    } catch (final IOException e) {
      throw new RuntimeException("Cannot make new Stream", e);
    }
  }

  public Date getLastModified() {
    return lastModified;
  }

  /**
   * Returns the mimeType.
   * 
   * @return the mimeType
   */
  public String getMimeType() {
    return mimeType;
  }

  public boolean isModified() {
    return modified;
  }
}
