package ch.bergturbenthal.image.server.state;

import java.util.Collection;

import ch.bergturbenthal.image.data.model.state.ProgressType;
import ch.bergturbenthal.image.data.model.state.ServerState;
import ch.bergturbenthal.image.server.util.ConflictEntry;

public interface StateManager {
  ProgressHandler newProgress(final int totalCount, final ProgressType type, final String progressDescription);

  CloseableProgressMonitor makeProgressMonitor();

  ServerState getCurrentState();

  void recordException(final String relativePath, final Throwable ex);

  void clearException(final String relativePath);

  void recordThumbnailException(final String name, final String image, final Throwable ex);

  void clearThumbnailException(final String name, final String image);

  void reportConflict(final String albumName, final Collection<ConflictEntry> conflicts);
}
