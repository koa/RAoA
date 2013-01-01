package ch.bergturbenthal.image.server.watcher;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatcher implements Closeable {
  private WatchService watchService;
  private Thread watcherThread;
  private static Logger logger = LoggerFactory.getLogger(FileWatcher.class);
  private final ExecutorService executorService;
  private final FileNotification notification;

  public FileWatcher(final File basePath, final ExecutorService executorService, final FileNotification notification) {
    this.executorService = executorService;
    this.notification = notification;
    try {
      final Path path = basePath.toPath();

      watchService = path.getFileSystem().newWatchService();
      path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

      final Runnable watchRunnable = new Runnable() {

        @Override
        public void run() {
          try {
            for (;;) {
              final WatchKey watchKey;
              try {
                watchKey = watchService.take();
              } catch (final InterruptedException e) {
                return;
              }
              try {
                for (final WatchEvent<?> event : watchKey.pollEvents()) {
                  final Path changedPath = (Path) event.context();
                  final File addedDirectory = path.resolve(changedPath).toFile();
                  processDirectory(addedDirectory);
                }
              } catch (final Throwable t) {
                logger.warn("Cannot process watch-event", t);
              }
              final boolean valid = watchKey.reset();
              if (!valid)
                break;

            }
          } finally {
            try {
              watchService.close();
            } catch (final IOException e) {
              logger.error("Cannot close watcher for " + basePath, e);
            }
          }
        }
      };
      watcherThread = new Thread(watchRunnable, "FileWatcher " + basePath);
      watcherThread.start();
      executorService.submit(new Runnable() {

        @Override
        public void run() {
          for (final File existingFile : basePath.listFiles()) {
            processDirectory(existingFile);
          }
        }
      });

    } catch (final IOException e) {
      throw new RuntimeException("Cannot watch " + basePath, e);
    }

  }

  @Override
  public void close() {
    watcherThread.interrupt();
  }

  private void processDirectory(final File directory) {
    if (!directory.isDirectory())
      return;
    final File clientIdFile = new File(directory, ".clientid");
    final File bareIdFile = new File(directory, ".bareid");
    if (clientIdFile.exists() && clientIdFile.canRead()) {
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          notification.notifySyncDiskPlugged(directory);
        }
      });
    } else if (bareIdFile.exists() && bareIdFile.canRead()) {
      executorService.execute(new Runnable() {

        @Override
        public void run() {
          notification.notifySyncBareDiskPlugged(directory);
        }
      });
    }
    final File dcimDirectory = new File(directory, "DCIM");
    if (dcimDirectory.exists() && dcimDirectory.isDirectory()) {
      executorService.execute(new Runnable() {

        @Override
        public void run() {
          notification.notifyCameraStorePlugged(dcimDirectory);
        }
      });
    }
  }
}
