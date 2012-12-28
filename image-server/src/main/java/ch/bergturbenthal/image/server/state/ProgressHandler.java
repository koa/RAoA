package ch.bergturbenthal.image.server.state;

import java.io.Closeable;

public interface ProgressHandler extends Closeable {
  @Override
  void close();

  void notfiyProgress(final int counter, final String stateDescription);

  void notfiyProgress(final String stateDescription);

  void finishProgress();
}
