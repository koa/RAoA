package ch.bergturbenthal.image.server.state;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

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
      final Progress progress = new Progress();
      progress.setProgressId(progressId);
      progress.setProgressDescription(taskName);
      progress.setType(ProgressType.GIT);
      runningProgress.put(progressId, progress);
      pushUpdates();
    }

    @Override
    protected void onUpdate(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
      final Progress progress = new Progress();
      progress.setProgressId(progressId);
      progress.setProgressDescription(taskName);
      progress.setType(ProgressType.GIT);
      progress.setStepCount(workTotal);
      progress.setCurrentStepNr(workCurr);
      runningProgress.put(progressId, progress);
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
      private final AtomicInteger lastCounter = new AtomicInteger(0);
      private final AtomicInteger doneCounter = new AtomicInteger(0);
      private boolean closed = false;

      @Override
      public void close() {
        closed = true;
        runningProgress.remove(progressId);
        pushUpdates();
      }

      @Override
      public void finishProgress() {
        if (doneCounter.incrementAndGet() >= totalCount) {
          close();
        }
      }

      @Override
      public void notfiyProgress(final int counter, final String stateDescription) {
        lastCounter.set(counter);
        updateState(counter, stateDescription);
      }

      @Override
      public void notfiyProgress(final String description) {
        updateState(lastCounter.incrementAndGet(), description);
      }

      private void updateState(final int counter, final String description) {
        if (closed)
          return;
        final Progress progress = new Progress();
        progress.setProgressId(progressId);
        progress.setStepCount(totalCount);
        progress.setCurrentStepNr(counter);
        progress.setProgressDescription(progressDescription);
        progress.setCurrentStepDescription(description);
        progress.setType(type);
        runningProgress.put(progressId, progress);
        pushUpdates();
      }
    };
  }

  private void pushUpdates() {
    // System.out.println("------------------------------------------");
    // for (final Progress progress : runningProgress.values()) {
    // System.out.println(progress);
    // }
    // System.out.println("------------------------------------------");
  }

}
