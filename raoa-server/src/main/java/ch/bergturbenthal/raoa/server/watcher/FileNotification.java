package ch.bergturbenthal.raoa.server.watcher;

import java.io.File;

public interface FileNotification {
	void notifyCameraStorePlugged(final File path);

	void notifySyncDiskPlugged(final File path);

	void notifySyncBareDiskPlugged(final File path);
}
