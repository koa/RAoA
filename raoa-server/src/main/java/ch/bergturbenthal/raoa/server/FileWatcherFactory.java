package ch.bergturbenthal.raoa.server;

import java.io.File;

import ch.bergturbenthal.raoa.server.watcher.FileWatcher;

public interface FileWatcherFactory {
	FileWatcher createWatcher(final File basePath);
}
