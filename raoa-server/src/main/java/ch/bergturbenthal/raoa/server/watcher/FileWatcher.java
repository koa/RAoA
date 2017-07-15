package ch.bergturbenthal.raoa.server.watcher;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileWatcher implements Closeable {

	public static FileWatcher createWatcher(final File basePath) {
		return new FileWatcher(basePath);
	}

	private final File basePath;
	@Autowired
	private ScheduledExecutorService executorService;
	@Autowired
	private DirectoryNotificationService notificationService;

	private ScheduledFuture<?> scheduledFuture;
	private Thread watcherThread;

	public FileWatcher(final File basePath) {
		this.basePath = basePath;
	}

	@Override
	public void close() {
		watcherThread.interrupt();
	}

	@PostConstruct
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

	@PreDestroy
	public void shutdownPolling() {
		if (scheduledFuture != null) {
			scheduledFuture.cancel(true);
		}
	}

}
