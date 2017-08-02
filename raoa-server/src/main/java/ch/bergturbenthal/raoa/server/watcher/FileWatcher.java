package ch.bergturbenthal.raoa.server.watcher;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileWatcher implements Closeable {

	private final File basePath;
	private final ScheduledExecutorService executorService;
	private final DirectoryNotificationService notificationService;

	private ScheduledFuture<?> scheduledFuture;
	private Thread watcherThread;

	public FileWatcher(final File basePath, final ScheduledExecutorService executorService, final DirectoryNotificationService notificationService) {
		this.basePath = basePath;
		this.executorService = executorService;
		this.notificationService = notificationService;
	}

	@Override
	public void close() {
		if (scheduledFuture != null) {
			scheduledFuture.cancel(true);
		}
		watcherThread.interrupt();
	}

	public void initPolling() {
		if (scheduledFuture != null) {
			scheduledFuture.cancel(false);
		}
		scheduledFuture = executorService.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				try {
					poll();
				} catch (final Exception ex) {
					log.error("Error while polling", ex);
				}

			}
		}, 20, 30, TimeUnit.SECONDS);
	}

	public void poll() {
		log.debug("Poll for directories");
		for (final File f : basePath.listFiles()) {
			if (!f.isDirectory()) {
				continue;
			}
			notificationService.notifyDirectory(f);
		}
	}

}
