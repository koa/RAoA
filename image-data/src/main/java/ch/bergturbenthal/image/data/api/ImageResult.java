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

  public ImageResult(final Date lastModified, final StreamSource dataStream) {
    super();
    this.lastModified = lastModified;
    this.dataStream = dataStream;
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
}
