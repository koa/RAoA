package ch.bergturbenthal.image.data.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ImageResult {
  public static interface StreamSource {
    InputStream getInputStream() throws IOException;
  }

  public static ImageResult makeModifiedResult(final Date lastModified, final Date created, final StreamSource dataStream) {
    return new ImageResult(lastModified, created, dataStream, true);
  }

  public static ImageResult makeNotModifiedResult() {
    return new ImageResult(null, null, null, false);
  }

  private final Date lastModified;
  private final StreamSource dataStream;
  private final Date created;
  private final boolean modified;

  private ImageResult(final Date lastModified, final Date created, final StreamSource dataStream, final boolean modified) {
    super();
    this.lastModified = lastModified;
    this.created = created;
    this.dataStream = dataStream;
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

  public boolean isModified() {
    return modified;
  }
}
