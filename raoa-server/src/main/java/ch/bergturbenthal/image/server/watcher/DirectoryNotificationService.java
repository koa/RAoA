/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.server.watcher;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

/**
 * TODO: add type comment.
 * 
 */
@Component
public class DirectoryNotificationService {
  @Autowired
  private ExecutorService executorService;
  @Autowired
  private FileNotification notification;
  private final Map<String, Future<?>> currentFutures = new HashMap<String, Future<?>>();

  public synchronized Future<?> notifyDirectory(final File directory) {
    if (!directory.isDirectory())
      return new AsyncResult<Object>(new Object());
    final String absolutePath = directory.getAbsolutePath();
    final Future<?> pendingFuture = currentFutures.get(absolutePath);
    if (pendingFuture != null && !pendingFuture.isDone())
      return new AsyncResult<Object>(new Object());
    final File clientIdFile = new File(directory, ".clientid");
    final File bareIdFile = new File(directory, ".bareid");
    if (clientIdFile.exists() && clientIdFile.canRead()) {
      final Future<?> future = executorService.submit(new Runnable() {
        @Override
        public void run() {
          notification.notifySyncDiskPlugged(directory);
        }
      });
      currentFutures.put(absolutePath, future);
      return future;
    } else if (bareIdFile.exists() && bareIdFile.canRead()) {
      final Future<?> future = executorService.submit(new Runnable() {

        @Override
        public void run() {
          notification.notifySyncBareDiskPlugged(directory);
        }
      });
      currentFutures.put(absolutePath, future);
      return future;
    }
    final File dcimDirectory = new File(directory, "DCIM");
    if (dcimDirectory.exists() && dcimDirectory.isDirectory()) {
      final Future<?> future = executorService.submit(new Runnable() {

        @Override
        public void run() {
          notification.notifyCameraStorePlugged(dcimDirectory);
        }
      });
      currentFutures.put(absolutePath, future);
      return future;
    }
    return new AsyncResult<Object>(new Object());
  }
}
