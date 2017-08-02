package ch.bergturbenthal.raoa.server.configuration;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.DaemonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumFactory;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.AlbumImageFactory;
import ch.bergturbenthal.raoa.server.AlbumRepositoryResolver;
import ch.bergturbenthal.raoa.server.FileAlbumAccess;
import ch.bergturbenthal.raoa.server.FileWatcherFactory;
import ch.bergturbenthal.raoa.server.cache.AlbumManager;
import ch.bergturbenthal.raoa.server.controller.PingController;
import ch.bergturbenthal.raoa.server.metadata.MetadataFactory;
import ch.bergturbenthal.raoa.server.metadata.MetadataFactoryImpl;
import ch.bergturbenthal.raoa.server.state.StateManager;
import ch.bergturbenthal.raoa.server.state.StateManagerImpl;
import ch.bergturbenthal.raoa.server.thumbnails.FfmpegVideoThumbnailMaker;
import ch.bergturbenthal.raoa.server.thumbnails.ImageMagickImageThumbnailMaker;
import ch.bergturbenthal.raoa.server.thumbnails.ImageThumbnailMaker;
import ch.bergturbenthal.raoa.server.thumbnails.VideoThumbnailMaker;
import ch.bergturbenthal.raoa.server.util.RepositoryService;
import ch.bergturbenthal.raoa.server.util.RepositoryServiceImpl;
import ch.bergturbenthal.raoa.server.watcher.DirectoryNotificationService;
import ch.bergturbenthal.raoa.server.watcher.FileWatcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// @EnableEurekaClient
@EnableScheduling
@ComponentScan(basePackageClasses = { PingController.class, DirectoryNotificationService.class })
@Configuration
public class ServerConfiguration {
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
			try {
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
			} catch (final Exception ex) {
				log.warn("Cannot close file watcher", ex);
			}
		}

		@Override
		public FileWatcher createWatcher(final File basePath) {
			return existingWatchers.computeIfAbsent(basePath, path -> {
				final FileWatcher fileWatcher = new FileWatcher(path, executorService, notificationService);
				fileWatcher.initPolling();
				return fileWatcher;
			});
		}
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
	public AlbumRepositoryResolver<DaemonClient> albumRepositoryResolver() {
		return new AlbumRepositoryResolver<>();
	}

	@Bean
	public ScheduledExecutorService executorService() {
		return Executors.newScheduledThreadPool(20);
	}

	@Bean
	public AlbumAccess fileAlbumAccess() {
		return new FileAlbumAccess();
	}

	@Bean
	public FileWatcherFactory fileWatcherFactory(final ScheduledExecutorService executorService, final DirectoryNotificationService notificationService) {
		return new DefaultFileWatcherFactory(executorService, notificationService);
	}

	@Bean
	public ImageThumbnailMaker imageThumbnailMaker() {
		final ImageMagickImageThumbnailMaker imageMagickImageThumbnailMaker = new ImageMagickImageThumbnailMaker();
		imageMagickImageThumbnailMaker.setThumbnailSize(1600);
		return imageMagickImageThumbnailMaker;
	}

	@Bean
	public Daemon jgitDaemon(final AlbumRepositoryResolver<DaemonClient> resolver) {
		final Daemon daemon = new Daemon();
		daemon.setRepositoryResolver(resolver);
		return daemon;
	}

	@Bean
	public MetadataFactory metadataFactory() {
		return new MetadataFactoryImpl();
	}

	@Bean
	public RepositoryService repositoryService() {
		return new RepositoryServiceImpl();
	}

	@Bean
	public StateManager stateManager() {
		return new StateManagerImpl();
	}

	@Bean
	public VideoThumbnailMaker videoThumbnailMaker() {
		return new FfmpegVideoThumbnailMaker();
	}

}
