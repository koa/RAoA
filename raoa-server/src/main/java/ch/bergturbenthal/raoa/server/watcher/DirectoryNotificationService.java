/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.server.watcher;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * TODO: add type comment.
 *
 */
@Slf4j
@Component
public class DirectoryNotificationService {
	private final Map<String, Future<?>> currentFutures = new HashMap<String, Future<?>>();
	@Autowired
	private ExecutorService executorService;
	@Autowired
	private FileNotification notification;

	public synchronized Future<?> notifyDirectory(final File directory) {
		if (!directory.isDirectory()) {
			if (log.isDebugEnabled()) {
				log.debug("no directory: " + directory);
			}
			return new AsyncResult<Object>(new Object());
		}
		final String absolutePath = directory.getAbsolutePath();
		final Future<?> pendingFuture = currentFutures.get(absolutePath);
		if (pendingFuture != null && !pendingFuture.isDone()) {
			if (log.isDebugEnabled()) {
				log.debug("Already loading " + directory);
			}
			return pendingFuture;
		}
		final File clientIdFile = new File(directory, ".clientid");
		final File bareIdFile = new File(directory, ".bareid");
		if (clientIdFile.exists() && clientIdFile.canRead()) {
			log.debug("Sync HD " + directory);
			final Future<?> future = executorService.submit(new Runnable() {
				@Override
				public void run() {
					notification.notifySyncDiskPlugged(directory);
				}
			});
			currentFutures.put(absolutePath, future);
			return future;
		} else if (bareIdFile.exists() && bareIdFile.canRead()) {
			log.debug("Sync bare HD " + directory);
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
			log.debug("Load images from " + directory);
			final Future<?> future = executorService.submit(new Runnable() {

				@Override
				public void run() {
					notification.notifyCameraStorePlugged(dcimDirectory);
				}
			});
			currentFutures.put(absolutePath, future);
			return future;
		}
		log.debug("Unsupported " + directory);
		return new AsyncResult<Object>(new Object());
	}
}
