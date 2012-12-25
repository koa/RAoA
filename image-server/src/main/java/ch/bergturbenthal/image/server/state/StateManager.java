package ch.bergturbenthal.image.server.state;

import ch.bergturbenthal.image.data.model.state.ProgressType;
import ch.bergturbenthal.image.data.model.state.ServerState;

public interface StateManager {
  ProgressHandler newProgress(final int totalCount, final ProgressType type, final String progressDescription);

  CloseableProgressMonitor makeProgressMonitor();

  ServerState getCurrentState();
}
