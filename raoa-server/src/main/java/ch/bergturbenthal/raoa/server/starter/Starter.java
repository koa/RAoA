package ch.bergturbenthal.raoa.server.starter;

import java.io.Closeable;
import java.io.File;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumFactory;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.AlbumImageFactory;
import ch.bergturbenthal.raoa.server.FileWatcherFactory;
import ch.bergturbenthal.raoa.server.cache.AlbumManager;
import ch.bergturbenthal.raoa.server.controller.PingController;
import ch.bergturbenthal.raoa.server.state.StateManager;
import ch.bergturbenthal.raoa.server.thumbnails.ImageThumbnailMaker;
import ch.bergturbenthal.raoa.server.thumbnails.VideoThumbnailMaker;
import ch.bergturbenthal.raoa.server.util.RepositoryService;
import ch.bergturbenthal.raoa.server.watcher.DirectoryNotificationService;
import ch.bergturbenthal.raoa.server.watcher.FileWatcher;

@SpringBootApplication
@ImportResource("classpath:spring/services.xml")
// @EnableEurekaClient
@EnableScheduling
@ComponentScan(basePackageClasses = { PingController.class, DirectoryNotificationService.class })
public class Starter {

	private static final class DefaultFileWatcherFactory implements FileWatcherFactory, Closeable {
		private final ScheduledExecutorService executorService;
		private final Map<File, FileWatcher> existingWatchers = Collections.synchronizedMap(new HashMap<>());
		private final DirectoryNotificationService notificationService;

		private DefaultFileWatcherFactory(final ScheduledExecutorService executorService, final DirectoryNotificationService notificationService) {
			this.executorService = executorService;
			this.notificationService = notificationService;
		}

		@Override
		public void close() {
			while (!existingWatchers.isEmpty()) {
				final Collection<File> watchedDirectories = new ArrayList<>(existingWatchers.keySet());
				for (final File file : watchedDirectories) {
					final FileWatcher fileWatcher = existingWatchers.remove(file);
					if (fileWatcher == null) {
						continue;
					}
					fileWatcher.close();
				}
			}
		}

		@Override
		public FileWatcher createWatcher(final File basePath) {
			return existingWatchers.computeIfAbsent(basePath, path -> new FileWatcher(path, executorService, notificationService));
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(Starter.class, args);
	}

	@Bean
	public AlbumFactory albumFactory(final RepositoryService repositoryService, final StateManager stateManager, final AlbumImageFactory albumImageFactory) {
		return new AlbumFactory() {

			@Override
			public Album createAlbum(final File baseDir, final String[] nameComps, final String remoteUri, final String serverName) {
				final Album album = new Album(baseDir, nameComps, remoteUri, serverName, repositoryService, stateManager, albumImageFactory);
				album.init();
				return album;
			}
		};
	}

	@Bean
	public AlbumImageFactory albumImageFactory(final ImageThumbnailMaker imageThumbnailMaker, final VideoThumbnailMaker videoThumbnailMaker) {
		return new AlbumImageFactory() {
			private final Map<File, Object> imageLocks = new WeakHashMap<File, Object>();
			private final Semaphore limitConcurrentScaleSemaphore = new Semaphore(4);
			private final Map<File, SoftReference<AlbumImage>> loadedImages = new ConcurrentHashMap<File, SoftReference<AlbumImage>>();

			@Override
			public AlbumImage createImage(final File file, final File cacheDir, final Date lastModified, final AlbumManager cacheManager) {
				synchronized (lockFor(file)) {
					final SoftReference<AlbumImage> softReference = loadedImages.get(file);
					if (softReference != null) {
						final AlbumImage cachedImage = softReference.get();
						if (cachedImage != null && cachedImage.getLastModified().equals(lastModified)) {
							return cachedImage;
						}
					}
					final AlbumImage newImage = new AlbumImage(file, cacheDir, lastModified, cacheManager, limitConcurrentScaleSemaphore, imageThumbnailMaker, videoThumbnailMaker);
					loadedImages.put(file, new SoftReference<AlbumImage>(newImage));
					return newImage;
				}
			}

			private synchronized Object lockFor(final File file) {
				final Object existingLock = imageLocks.get(file);
				if (existingLock != null) {
					return existingLock;
				}
				final Object newLock = new Object();
				imageLocks.put(file, newLock);
				return newLock;
			}
		};
	}

	@Bean
	public FileWatcherFactory fileWatcherFactory(final ScheduledExecutorService executorService, final DirectoryNotificationService notificationService) {
		return new DefaultFileWatcherFactory(executorService, notificationService);
	}

}
