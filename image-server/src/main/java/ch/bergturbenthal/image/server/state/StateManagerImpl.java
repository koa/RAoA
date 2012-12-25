package ch.bergturbenthal.image.server.state;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jgit.lib.BatchingProgressMonitor;

import ch.bergturbenthal.image.data.model.state.Progress;
import ch.bergturbenthal.image.data.model.state.ProgressType;
import ch.bergturbenthal.image.data.model.state.ServerState;

public class StateManagerImpl implements StateManager {

  private class JGitProgressMonitor extends BatchingProgressMonitor implements CloseableProgressMonitor {
    private final String progressId = UUID.randomUUID().toString();

    @Override
    public void close() throws IOException {
      runningProgress.remove(progressId);
      pushUpdates();
    }

    @Override
    protected void onEndTask(final String taskName, final int workCurr) {
      runningProgress.remove(progressId);
      pushUpdates();
    }

    @Override
    protected void onEndTask(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
      runningProgress.remove(progressId);
      pushUpdates();
    }

    @Override
    protected void onUpdate(final String taskName, final int workCurr) {
      runningProgress.put(progressId, new Progress(progressId, 0, 0, taskName, null, ProgressType.GIT));
      pushUpdates();
    }

    @Override
    protected void onUpdate(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
      runningProgress.put(progressId, new Progress(progressId, workTotal, workCurr, taskName, null, ProgressType.GIT));
      pushUpdates();
    }
  }

  private final ConcurrentMap<String, Progress> runningProgress = new ConcurrentHashMap<String, Progress>();

  @Override
  public ServerState getCurrentState() {
    final ServerState serverState = new ServerState();
    serverState.setProgress(runningProgress.values());
    return serverState;
  }

  @Override
  public CloseableProgressMonitor makeProgressMonitor() {

    return new JGitProgressMonitor();
  }

  @Override
  public ProgressHandler newProgress(final int totalCount, final ProgressType type, final String progressDescription) {
    final String progressId = UUID.randomUUID().toString();
    return new ProgressHandler() {
      int lastCounter = 0;

      @Override
      public void close() {
        runningProgress.remove(progressId);
        pushUpdates();
      }

      @Override
      public void notfiyProgress(final int counter, final String stateDescription) {
        lastCounter = counter;
        updateState(counter, stateDescription);
      }

      @Override
      public void notfiyProgress(final String description) {
        updateState(++lastCounter, description);
      }

      private void updateState(final int counter, final String description) {
        runningProgress.put(progressId, new Progress(progressId, totalCount, counter, progressDescription, description, type));
        pushUpdates();
      }
    };
  }

  private void pushUpdates() {
    System.out.println("------------------------------------------");
    for (final Progress progress : runningProgress.values()) {
      System.out.println(progress);
    }
    System.out.println("------------------------------------------");
  }

}
