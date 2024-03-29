package ch.bergturbenthal.raoa.server;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.data.model.StorageEntry;
import ch.bergturbenthal.raoa.data.model.mutation.MetadataMutation;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.data.model.mutation.StorageMutation;
import ch.bergturbenthal.raoa.data.model.state.ProgressType;
import ch.bergturbenthal.raoa.data.util.ExecutorServiceUtil;
import ch.bergturbenthal.raoa.server.metadata.MetadataHolder;
import ch.bergturbenthal.raoa.server.metadata.MetadataWrapper;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.model.ArchiveData;
import ch.bergturbenthal.raoa.server.model.StorageData;
import ch.bergturbenthal.raoa.server.model.StorageStatistics;
import ch.bergturbenthal.raoa.server.state.ProgressHandler;
import ch.bergturbenthal.raoa.server.state.StateManager;
import ch.bergturbenthal.raoa.server.store.LocalStore;
import ch.bergturbenthal.raoa.server.thumbnails.ThumbnailSize;
import ch.bergturbenthal.raoa.server.util.ConcurrentUtil;
import ch.bergturbenthal.raoa.server.util.RepositoryService;
import ch.bergturbenthal.raoa.server.watcher.FileNotification;
import ch.bergturbenthal.raoa.server.watcher.FileWatcher;
import ch.bergturbenthal.raoa.util.store.FileStorage.ReadPolicy;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileAlbumAccess implements AlbumAccess, StorageAccess, FileConfiguration, ArchiveConfiguration, FileNotification {
	private static final String ALBUM_PATH_PREFERENCE = "album_path";
	private static final String CLIENTID_FILENAME = ".clientid";
	private static final String IMPORT_BASE_PATH_REFERENCE = "import_base_path";
	private static final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private static final String META_CACHE = "cache";
	private static final String META_REPOSITORY = ".meta";
	private static final String QUOTED_FILE_SEPARATOR = Pattern.quote(File.separator);
	private static final String SERVICE_TYPE = "_images._tcp.local.";
	private static final File TEMP_DIR;

	static {
		TEMP_DIR = new File(System.getProperty("java.io.tmpdir"));
	}
	@Autowired
	private AlbumFactory albumFactory;

	private File baseDir;
	@Value("${raoa.album:}")
	private File configuredAlbumBasePath;
	@Value("${raoa.import:}")
	private File configuredImportBasePath;
	private final ConcurrentMap<String, Object> createAlbumLocks = new ConcurrentHashMap<String, Object>();
	@Autowired
	private ScheduledExecutorService executorService;

	private FileWatcher fileWatcher = null;
	@Autowired
	private FileWatcherFactory fileWatcherFactory;
	private File importBaseDir;
	private final String instanceId = UUID.randomUUID().toString();
	private String instanceName;
	private final Object instanceNameLoadLock = new Object();
	private JmmDNS jmmDNS;
	private final AtomicLong lastLoadedDate = new AtomicLong(0);
	private final Map<String, Album> loadedAlbums = new ConcurrentHashMap<String, Album>();
	private Git metaGit;;
	private final ReadWriteLock metaRwLock = new ReentrantReadWriteLock();
	private Preferences preferences = null;
	private final Object processPeersLock = new Object();
	private final Semaphore refreshThumbnailsSemaphore = new Semaphore(1);
	@Autowired
	private RepositoryService repositoryService;
	private final RestTemplate restTemplate = new RestTemplate();
	private ExecutorService safeExecutorService;
	@Autowired
	private StateManager stateManager;

	private LocalStore store;

	private final ExecutorService syncExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), createSynchThreadFactory());
	private final Semaphore updateAlbumListSemaphore = new Semaphore(1);

	private Album appendAlbum(final Map<String, Album> albumMap, final File albumDir, final String remoteUri, final String serverName) {
		final String[] nameComps = evaluateNameComps(albumDir);
		final String albumId = makeAlbumId(nameComps);
		final Album album;
		synchronized (getAlbumLock(albumDir)) {
			final Album existingAlbum = albumMap.get(albumId);
			if (existingAlbum == null) {
				final Album newAlbum = albumFactory.createAlbum(albumDir, nameComps, remoteUri, serverName);
				albumMap.put(albumId, newAlbum);
				album = newAlbum;
			} else {
				album = existingAlbum;
			}
		}
		album.reCheck();
		return album;
	}

	private String cleanAlbumName(final boolean bare, final String relativeDirectoryName) {
		if (bare) {
			if (!relativeDirectoryName.endsWith(".git")) {
				log.error(relativeDirectoryName + " not ends with .git");
				return null;
			}
			return relativeDirectoryName.substring(0, relativeDirectoryName.length() - 4);
		}
		return relativeDirectoryName;
	}

	@Override
	public Collection<String> clientsPerAlbum(final String albumId) {
		final String albumName = Util.decodeStringOfUrl(albumId);
		final HashSet<String> ret = new HashSet<String>();
		for (final Entry<String, StorageData> albumEntry : store.getArchiveData(ReadPolicy.READ_ONLY).getStorages().entrySet()) {
			if (albumEntry.getValue().getAlbumList().contains(albumName)) {
				ret.add(albumEntry.getKey());
			}
		}
		return ret;
	}

	private SortedMap<Date, Album> collectImportAlbums() {
		final SortedMap<Date, Album> importAlbums = new TreeMap<Date, Album>();
		for (final Album album : loadAlbums(true).values()) {
			for (final Date beginDate : album.getAutoAddBeginDate()) {
				if (beginDate != null) {
					importAlbums.put(beginDate, album);
				}
			}
		}
		return importAlbums;
	}

	private Collection<File> collectImportFiles(final File importDir) {
		if (!importDir.isDirectory()) {
			return Collections.emptyList();
		}
		final ArrayList<File> ret = new ArrayList<File>();
		ret.addAll(Arrays.asList(importDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				if (!pathname.canRead()) {
					return false;
				}
				if (!pathname.isFile()) {
					return false;
				}
				final String lowerFilename = pathname.getName().toLowerCase();
				return lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".nef") || lowerFilename.endsWith(".jpeg");
			}
		})));
		for (final File dir : importDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				return pathname.isDirectory();
			}
		})) {
			ret.addAll(collectImportFiles(dir));
		}
		return ret;
	}

	private void commitNeededFiles(final String message, final Git repo) throws GitAPIException {
		final Status status = repo.status().call();
		if (!status.isClean()) {
			boolean fileFound = false;
			final AddCommand addCommand = repo.add();
			final Set<String> modified = status.getModified();
			if (modified != null && !modified.isEmpty()) {
				for (final String modifiedFile : modified) {
					fileFound = true;
					addCommand.addFilepattern(modifiedFile);
				}
			}
			final Set<String> untracked = status.getUntracked();
			if (untracked != null && !untracked.isEmpty()) {
				for (final String untrackedFile : untracked) {
					fileFound = true;
					addCommand.addFilepattern(untrackedFile);
				}
			}

			if (fileFound) {
				addCommand.call();
				repo.commit().setMessage(message).call();
			} else {
				log.warn("Repository modified " + repo.getRepository());
			}
		}
	}

	private void configureFromPreferences() {
		if (getBaseDir() == null) {
			preferences = Preferences.userNodeForPackage(FileAlbumAccess.class);
			readLocalSettingsFromPreferences();
			preferences.addPreferenceChangeListener(new PreferenceChangeListener() {
				@Override
				public void preferenceChange(final PreferenceChangeEvent arg0) {
					readLocalSettingsFromPreferences();
				}
			});
		}
	}

	@Override
	public Album createAlbum(final String[] pathNames) {
		final Map<String, Album> albums = loadAlbums(true);
		final File basePath = getBasePath();
		File newAlbumPath = basePath;
		for (final String pathComp : pathNames) {
			newAlbumPath = new File(newAlbumPath, pathComp);
		}
		if (!newAlbumPath.getAbsolutePath().startsWith(basePath.getAbsolutePath())) {
			throw new RuntimeException("Cannot create Album " + pathNames);
		}
		if (newAlbumPath.exists()) {
			for (final Entry<String, Album> albumEntry : albums.entrySet()) {
				final Album existingAlbum = albumEntry.getValue();
				if (Arrays.asList(pathNames).equals(existingAlbum.getNameComps())) {
					// album already exists
					return albumEntry.getValue();
				}
			}
			throw new RuntimeException("Directory " + newAlbumPath + " already exsists");
		}
		if (!newAlbumPath.exists()) {
			final boolean createParent = newAlbumPath.mkdirs();
			if (!createParent) {
				throw new RuntimeException("Cannot create Directory " + newAlbumPath);
			}
		}

		return appendAlbum(loadedAlbums, newAlbumPath, null, null);
	}

	private FileWatcher createFileWatcher() {
		return fileWatcherFactory.createWatcher(importBaseDir);
	}

	private CustomizableThreadFactory createSynchThreadFactory() {
		final CustomizableThreadFactory tf = new CustomizableThreadFactory("sync-thread");
		tf.setThreadPriority(Thread.MIN_PRIORITY);
		return tf;
	}

	private void doLoadAlbums(final boolean forceWait) {
		if (forceWait) {
			try {
				updateAlbumListSemaphore.acquire();
			} catch (final InterruptedException e) {
				// interrupted
				return;
			}
		} else {
			final boolean hasLock = updateAlbumListSemaphore.tryAcquire();
			if (!hasLock) {
				return;
			}
		}
		try {
			if (needToLoadAlbumList()) {
				try {
					final Map<String, Album> ret = new ConcurrentHashMap<String, Album>();
					if (loadedAlbums != null) {
						ret.putAll(loadedAlbums);
					}
					final File basePath = getBasePath();
					log.debug("Load Repositories from: " + basePath);
					final int basePathLength = basePath.getAbsolutePath().length();
					final Map<String, Future<String>> futures = new HashMap<String, Future<String>>();
					for (final File albumDir : findAlbums(basePath, false)) {
						futures.put(albumDir.getName(), safeExecutorService.submit(new Callable<String>() {

							@Override
							public String call() throws Exception {
								log.debug("Load Repository " + albumDir);
								final String relativePath = albumDir.getAbsolutePath().substring(basePathLength + 1);
								if (relativePath.equals(META_REPOSITORY)) {
									return null;
								}
								try {
									final Album newAlbum = appendAlbum(loadedAlbums, albumDir, null, null);
									stateManager.clearException(relativePath);
									return makeAlbumId(newAlbum.getNameComps());
								} catch (final BeanCreationException ex) {
									stateManager.recordException(relativePath, ex);
									return null;
								}
							}
						}));
					}
					final Set<String> foundAlbums = new HashSet<>();
					for (final Entry<String, Future<String>> futureEntry : futures.entrySet()) {
						try {
							final Future<String> future = futureEntry.getValue();
							final String albumId = future.get();
							if (albumId != null) {
								foundAlbums.add(albumId);
							}
						} catch (final Exception e) {
							log.error("Cannot load album " + futureEntry.getKey(), e);
						}
					}
					loadedAlbums.keySet().retainAll(foundAlbums);
					lastLoadedDate.set(System.currentTimeMillis());
					long archiveSize = 0;
					for (final Album album : ret.values()) {
						archiveSize += album.getRepositorySize();
						for (final AlbumImage image : album.listImages().values()) {
							archiveSize += image.getAllFilesSize();
						}
					}
					final long availableSize = archiveSize + getBaseDir().getFreeSpace();
					final int availableGBytes = (int) (availableSize / 1024 / 1024 / 1024);
					final String storageName = getInstanceName();
					updateMeta("available size of " + storageName + " updated", new Callable<Void>() {

						@Override
						public Void call() throws Exception {
							final ArchiveData archiveData = store.getArchiveData(ReadPolicy.READ_OR_CREATE);
							if (archiveData.getStorages() == null) {
								archiveData.setStorages(new HashMap<String, StorageData>());
							}
							final Map<String, StorageData> storages = archiveData.getStorages();
							if (storages.containsKey(storageName)) {
								storages.get(storageName).setGBytesAvailable(availableGBytes);
							} else {
								final StorageData storageData = new StorageData();
								for (final Album album : ret.values()) {
									storageData.getAlbumList().add(album.getName());
								}
								storageData.setGBytesAvailable(availableGBytes);
								storages.put(storageName, storageData);
							}
							return null;
						}
					});
				} catch (final Throwable e) {
					if (forceWait) {
						throw new RuntimeException("Troubles while accessing resource " + getBaseDir(), e);
					} else {
						log.error("Troubles while accessing resource " + getBaseDir(), e);
					}
				}
			}
		} finally {
			updateAlbumListSemaphore.release();
		}
	}

	private String[] evaluateNameComps(final File albumDir) {
		final String relativePath = albumDir.getAbsolutePath().substring(getBasePath().getAbsolutePath().length() + 1);
		final String[] parts = relativePath.split(QUOTED_FILE_SEPARATOR);
		return parts;
	}

	private Collection<String> evaluateRepositoriesToSync(final String instanceName, final Set<String> existingRepositories, final ArchiveData config) {
		final Collection<String> ret = new HashSet<>(existingRepositories);
		if (config != null && config.getStorages().containsKey(instanceName)) {
			final StorageData storageData = config.getStorages().get(instanceName);
			if (!storageData.isTakeAllRepositories()) {
				ret.retainAll(storageData.getAlbumList());
			}
		}
		return ret;
	}

	private Album findAlbumForDate(final SortedMap<Date, Album> importAlbums, final Date createDate) {
		if (createDate == null) {
			return null;
		}
		final SortedMap<Date, Album> entriesBeforeDate = importAlbums.headMap(createDate);
		if (entriesBeforeDate.isEmpty()) {
			return null;
		}
		return entriesBeforeDate.get(entriesBeforeDate.lastKey());
	}

	private Collection<File> findAlbums(final File dir, final boolean pure) {
		if (repositoryService.isRepository(dir, pure)) {
			return Collections.singleton(dir);
		}
		final File[] foundFiles = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				return pathname.isDirectory();
			}
		});
		final ArrayList<File> ret = new ArrayList<File>();
		if (foundFiles != null) {
			Arrays.sort(foundFiles);
			for (final File subDir : foundFiles) {
				ret.addAll(findAlbums(subDir, pure));
			}
		}
		return ret;
	}

	private RevCommit findLatestMetaCommit() {
		try {
			final Iterator<RevCommit> log = metaGit.log().setMaxCount(1).call().iterator();
			if (log.hasNext()) {
				return log.next();
			}
			return null;
		} catch (final GitAPIException e) {
			throw new RuntimeException("Cannot read commit-log", e);
		}

	}

	@Override
	public Album getAlbum(final String albumId) {
		return loadAlbums(false).get(albumId);
	}

	private Object getAlbumLock(final File albumFile) {
		final String key = albumFile.getAbsolutePath();
		if (createAlbumLocks.containsKey(key)) {
			return createAlbumLocks.get(key);
		}
		createAlbumLocks.putIfAbsent(key, new Object());
		return createAlbumLocks.get(key);

	}

	@Override
	public String getArchiveName() {
		return store.getArchiveData(ReadPolicy.READ_ONLY).getArchiveName();
	}

	@Override
	public File getBaseDir() {
		return baseDir;
	}

	private File getBasePath() {
		return getBaseDir().getAbsoluteFile();
	}

	@Override
	public String getCollectionId() {
		return store.getArchiveData(ReadPolicy.READ_ONLY).getArchiveName();
	}

	private File getConfigFile() {
		return new File(getMetaDir(), "config.json");
	}

	@Override
	public File getImportBaseDir() {
		return importBaseDir;
	}

	@Override
	public String getInstanceId() {
		return instanceId;
	}

	@Override
	public String getInstanceName() {
		if (instanceName == null) {
			synchronized (instanceNameLoadLock) {
				final File inFile = makeClientIdFile();
				if (!inFile.exists()) {
					return null;
				}
				try {
					@Cleanup
					final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "utf-8"));
					instanceName = reader.readLine();
				} catch (final IOException e) {
					throw new RuntimeException("Cannot read " + inFile, e);
				}

			}
		}
		return instanceName;
	}

	private File getMetaDir() {
		final File metaDir = new File(getBasePath(), META_REPOSITORY);
		if (!metaDir.exists()) {
			metaDir.mkdirs();
		}
		return metaDir;
	}

	@Override
	public Repository getMetaRepository() {
		return metaGit.getRepository();
	}

	private File getServercacheDir() {
		final File cacheDir = new File(getMetaDir(), ".servercache");
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
		return cacheDir;
	}

	@Override
	public StorageStatistics getStatistics() {
		try {
			final File statFile = new File(getServercacheDir(), "statistics.json");
			if (!statFile.exists()) {
				return null;
			}
			return mapper.readValue(statFile, StorageStatistics.class);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read Statistics data", e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.server.AlbumAccess#importFile(java.lang.String, byte[])
	 */
	@Override
	public void importFile(final String filename, final byte[] data) {
		try {
			final File inFile = new File(filename);
			final File inDir = new File(TEMP_DIR, System.currentTimeMillis() + "-in");
			inDir.mkdirs();
			final File tempInFile = new File(inDir, inFile.getName());
			{
				@Cleanup
				final OutputStream os = new FileOutputStream(tempInFile);
				IOUtils.write(data, os);
			}
			importInternal(inDir);
			inDir.delete();
		} catch (final IOException e) {
			throw new RuntimeException("Cannot import File " + filename, e);
		}
	}

	@Override
	public void importFiles(final File importDir) {
		if (!importDir.getAbsolutePath().startsWith(importBaseDir.getAbsolutePath())) {
			log.error("Secutity-Error: Not allowed to read Images from " + importDir + " (Import-Path is " + importBaseDir + ")");
			return;
		}
		importInternal(importDir);
	}

	private void importInternal(final File importDir) {
		try {
			final HashSet<Album> modifiedAlbums = new HashSet<Album>();
			final SortedMap<Date, Album> importAlbums = collectImportAlbums();
			final Collection<File> deleteFiles = new ArrayList<File>();
			final Collection<File> collectImportFiles = collectImportFiles(importDir);
			if (log.isDebugEnabled()) {
				log.debug("Collected " + collectImportFiles.size() + " files");
			}
			for (final File file : collectImportFiles) {
				try {
					log.debug("Read: " + file.getName());
					final Metadata metadata = ImageMetadataReader.readMetadata(file);
					if (metadata == null) {
						continue;
					}
					final MetadataHolder metadataWrapper = new MetadataWrapper(metadata);
					final Date createDate = metadataWrapper.readCreateDate();
					final Album album = findAlbumForDate(importAlbums, createDate);
					if (album == null) {
						// no album found
						continue;
					}
					log.debug(" ->" + album.getName());
					if (album.importImage(file, createDate)) {
						modifiedAlbums.add(album);
						log.debug("image " + file + " imported successfully to " + album.getName());
						deleteFiles.add(file);
					} else {
						log.warn("Could not import image " + file);
					}
				} catch (final ImageProcessingException e) {
					throw new RuntimeException("Cannot import file " + file, e);
				} catch (final IOException e) {
					throw new RuntimeException("Cannot import file " + file, e);
				}
			}
			for (final Album album : modifiedAlbums) {
				album.commit("automatically imported");
			}
			for (final File file : deleteFiles) {
				final boolean deleted = file.delete();
				if (!deleted) {
					log.error("Cannot delete File " + file);
				}
			}
		} catch (final Exception ex) {
			log.error("Cannot load images", ex);
		} finally {
			log.debug("Load finished");
			refreshCache(true);
		}
	}

	@PostConstruct
	private void init() {
		configureFromPreferences();
		executorService.schedule(new Runnable() {

			@Override
			public void run() {
				try {
					if (importBaseDir != null && executorService != null) {
						fileWatcher = createFileWatcher();
					}
				} catch (final Exception ex) {
					log.error("Cannot start filewatcher", ex);
				}
			}
		}, 30, TimeUnit.SECONDS);
		executorService.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				pollCurrentKnownPeers();
			}
		}, 0, 5, TimeUnit.MINUTES);
	}

	@PostConstruct
	public void initExecutorService() {
		safeExecutorService = ExecutorServiceUtil.wrap(executorService);
	}

	@Override
	public Map<String, Album> listAlbums() {
		return new HashMap<>(loadAlbums(false));
	}

	@PostConstruct
	private void listenPeers() {
		if (jmmDNS != null) {
			return;
		}
		jmmDNS = JmmDNS.Factory.getInstance();
		final ServiceListener serviceListener = new ServiceListener() {

			@Override
			public void serviceAdded(final ServiceEvent event) {
				event.getDNS().requestServiceInfo(event.getType(), event.getName());
			}

			@Override
			public void serviceRemoved(final ServiceEvent event) {
				pollCurrentKnownPeers();
			}

			@Override
			public void serviceResolved(final ServiceEvent event) {
				pollCurrentKnownPeers();
			}
		};
		jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

			@Override
			public void inetAddressAdded(final NetworkTopologyEvent event) {
				event.getDNS().addServiceListener(SERVICE_TYPE, serviceListener);
			}

			@Override
			public void inetAddressRemoved(final NetworkTopologyEvent event) {
				pollCurrentKnownPeers();
			}
		});
	}

	@Override
	public synchronized ArchiveMeta listKnownStorage() {
		final ArchiveMeta storageList = new ArchiveMeta();
		final Map<String, StorageData> storages = store.getArchiveData(ReadPolicy.READ_ONLY).getStorages();
		for (final Entry<String, StorageData> storageEntry : storages.entrySet()) {
			final String name = storageEntry.getKey();
			final StorageEntry entry = new StorageEntry();
			entry.setStorageName(name);
			entry.setStorageId(Util.encodeStringForUrl(name));
			final StorageData storageData = storageEntry.getValue();
			final int gBytesAvailable = storageData.getGBytesAvailable();
			if (gBytesAvailable != Integer.MAX_VALUE) {
				entry.setGBytesAvailable((long) gBytesAvailable);
			}
			for (final String albumName : storageData.getAlbumList()) {
				entry.getAlbumList().add(Util.encodeStringForUrl(albumName));
			}
			entry.setTakeAllRepositories(storageData.isTakeAllRepositories());
			storageList.getClients().add(entry);
		}
		final RevCommit latestMetaCommit = findLatestMetaCommit();
		if (latestMetaCommit != null) {
			storageList.setVersion(latestMetaCommit.getId().name());
			storageList.setLastModified(new Date(latestMetaCommit.getCommitTime() * 1000));
		}
		return storageList;
	}

	private Map<String, Album> loadAlbums(final boolean wait) {
		if (needToLoadAlbumList()) {
			if (wait) {
				doLoadAlbums(true);
			} else {
				executorService.submit(new Runnable() {
					@Override
					public void run() {
						doLoadAlbums(false);
					}
				});
			}
		}
		return loadedAlbums;
	}

	private void loadMetaConfig() {
		final File metaDir = getMetaDir();

		if (new File(metaDir, ".git").exists()) {
			try {
				metaGit = Git.open(metaDir);
			} catch (final IOException e) {
				throw new RuntimeException("Cannot access to git-repository of " + getBaseDir(), e);
			}
		} else {
			try {
				metaGit = Git.init().setDirectory(metaDir).call();
			} catch (final GitAPIException e) {
				throw new RuntimeException("Cannot create meta repository", e);
			}
		}
		final File gitignore = new File(metaDir, ".gitignore");
		if (!gitignore.exists()) {
			try {
				@Cleanup
				final PrintWriter ignoreWriter = new PrintWriter(gitignore);
				ignoreWriter.println(".servercache");
			} catch (final FileNotFoundException e) {
				throw new RuntimeException("Cannot creare .gitignore", e);
			}
		}
		final File cacheDir = new File(metaDir, META_CACHE);
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}

		updateMeta("config.json built", new Callable<Void>() {

			@Override
			public Void call() {
				final ArchiveData archiveData = store.getArchiveData(ReadPolicy.READ_OR_CREATE);
				if (archiveData.getArchiveName() == null) {
					archiveData.setArchiveName(UUID.randomUUID().toString());
				}
				return null;
			}
		});
	}

	public ArchiveData loadMetaConfigFile(final File configFile) {
		try {
			final ArchiveData readValue = mapper.readValue(configFile, ArchiveData.class);
			return readValue;
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read meta-config from " + configFile, e);
		}
	}

	private String makeAlbumId(final File albumDir) {
		return makeAlbumId(evaluateNameComps(albumDir));
	}

	private String makeAlbumId(final List<String> nameComps) {
		return Util.encodeStringForUrl(StringUtils.join(nameComps, "/"));
	}

	private String makeAlbumId(final String[] nameComps) {
		return Util.encodeStringForUrl(StringUtils.join(nameComps, "/"));
	}

	private File makeClientIdFile() {
		return new File(getBaseDir(), CLIENTID_FILENAME);
	}

	private String makeDefaultInstanceName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (final UnknownHostException e) {
			return UUID.randomUUID().toString();
		}
	}

	private String makeRepositoryDirectoryName(final boolean pure, final String baseName) {
		if (pure) {
			return baseName + ".git";
		}
		return baseName;
	}

	private boolean needToLoadAlbumList() {
		return loadedAlbums == null || (System.currentTimeMillis() - lastLoadedDate.get()) > TimeUnit.MINUTES.toMillis(5);
	}

	@Override
	public void notifyCameraStorePlugged(final File path) {
		importFiles(path);
	}

	@Override
	public void notifySyncBareDiskPlugged(final File path) {
		syncExternal(path, true);
	}

	@Override
	public void notifySyncDiskPlugged(final File path) {
		syncExternal(path, false);
	}

	private ResponseEntity<PingResponse> ping(final URI uri) {
		return restTemplate.getForEntity(uri.resolve("ping.json"), PingResponse.class);
	}

	private void pollCurrentKnownPeers() {
		processFoundServices(jmmDNS.list(SERVICE_TYPE));
		refreshCache(true);
	}

	private void processFoundServices(final ServiceInfo[] services) {
		synchronized (processPeersLock) {

			final Map<String, URI> foundPeers = new HashMap<String, URI>();
			final List<InetSocketAddress> checkCandidates = new ArrayList<>();
			checkCandidates.add(new InetSocketAddress("royalarchive.lan", 8080));
			checkCandidates.add(new InetSocketAddress("royalarchive.lan", 80));
			for (final ServiceInfo serviceInfo : services) {
				final int peerPort = serviceInfo.getPort();
				final InetAddress[] addresses = serviceInfo.getInetAddresses();
				for (final InetAddress inetAddress : addresses) {
					if (inetAddress.isLinkLocalAddress()) {
						continue;
					}
					final InetSocketAddress peerCandidate = new InetSocketAddress(inetAddress, peerPort);
					checkCandidates.add(peerCandidate);
				}
			}
			for (final InetSocketAddress peerCandidate : checkCandidates) {
				final Collection<URI> availableURIs = new ArrayList<>();
				try {
					availableURIs.add(new URI("http", null, peerCandidate.getAddress().getHostAddress(), peerCandidate.getPort(), "/rest/", null, null));
				} catch (final URISyntaxException e1) {
					log.warn("Cannot build ip only uri of " + peerCandidate, e1);
				}
				try {
					availableURIs.add(new URI("http", null, peerCandidate.getAddress().getHostName(), peerCandidate.getPort(), "/rest/", null, null));
				} catch (final URISyntaxException e1) {
					log.warn("Cannot build named uri of " + peerCandidate, e1);
				}
				for (final URI candidateUri : availableURIs) {

					try {
						log.info("Try: " + candidateUri);
						final ResponseEntity<PingResponse> responseEntity = ping(candidateUri);
						if (!responseEntity.hasBody() || responseEntity.getStatusCode().series() != Series.SUCCESSFUL) {
							continue;
						}
						final PingResponse pingResponse = responseEntity.getBody();
						if (!pingResponse.getArchiveId().equals(getArchiveName())) {
							continue;
						}
						if (pingResponse.getServerId().equals(getInstanceId())) {
							continue;
						}
						foundPeers.put(pingResponse.getServerId(), candidateUri);
					} catch (final RestClientException e) {
						log.warn("ping " + peerCandidate, e);
					}
				}

			}
			log.info("Found peers: ");
			for (final Entry<String, URI> peerEntry : foundPeers.entrySet()) {
				log.info(" - " + peerEntry.getKey() + ": " + peerEntry.getValue());
			}
			updateAllRepositories(foundPeers.values());
		}
	}

	public String readClientId(final File path, final boolean bare) throws IOException, FileNotFoundException {
		String remoteName = null;
		@SuppressWarnings("unchecked")
		final List<String> clientIdLines = IOUtils.readLines(new FileInputStream(new File(path, bare ? ".bareid" : CLIENTID_FILENAME)), "utf-8");
		for (final String line : clientIdLines) {
			if (!line.trim().isEmpty()) {
				remoteName = line.trim();
			}
		}
		return remoteName;
	}

	private void readLocalSettingsFromPreferences() {
		if (configuredAlbumBasePath != null && configuredAlbumBasePath.exists()) {
			setBaseDir(configuredAlbumBasePath);
		} else {
			setBaseDir(new File(preferences.get(ALBUM_PATH_PREFERENCE, new File(System.getProperty("user.home"), "images").getAbsolutePath())));
		}
		if (configuredImportBasePath != null && configuredImportBasePath.exists()) {
			setImportBaseDir(configuredImportBasePath);
		} else {
			setImportBaseDir(new File(preferences.get(IMPORT_BASE_PATH_REFERENCE, "nowhere")));
		}
	}

	private void refreshCache(final boolean wait) {
		ConcurrentUtil.executeSequencially(refreshThumbnailsSemaphore, wait, new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				try {
					final AtomicInteger imageCount = new AtomicInteger();
					final ConcurrentMap<String, AtomicInteger> countByTag = new ConcurrentHashMap<>();

					// limit the queue size for take not too much memory
					final Semaphore queueLimitSemaphore = new Semaphore(100);
					final long startTime = System.currentTimeMillis();
					final Collection<Album> albums = new ArrayList<Album>(loadAlbums(wait).values());
					@Cleanup
					final ProgressHandler albumProgress = stateManager.newProgress(albums.size(), ProgressType.REFRESH_THUMBNAIL, instanceName);
					for (final Album album : albums) {
						@Cleanup
						final Closeable albumStep = albumProgress.notfiyProgress(album.getName());
						final Collection<AlbumImage> images = album.listImages().values();
						// final ProgressHandler thumbnailProgress =
						// stateManager.newProgress(images.size(),
						// ProgressType.REFRESH_THUMBNAIL, album.getName());
						for (final AlbumImage image : images) {
							queueLimitSemaphore.acquire();
							executorService.submit(new Runnable() {

								@Override
								public void run() {
									try {
										// thumbnailProgress.notfiyProgress(image.getName());
										// read Metadata
										image.captureDate();
										// read Thumbnail
										final AlbumEntryData albumEntryData = image.getAlbumEntryData();
										if (albumEntryData != null && albumEntryData.getKeywords() != null) {
											for (final String keyword : albumEntryData.getKeywords()) {
												countByTag.putIfAbsent(keyword, new AtomicInteger(0));
												countByTag.get(keyword).incrementAndGet();
											}
										}
									} finally {
										imageCount.incrementAndGet();
										queueLimitSemaphore.release();
										// thumbnailProgress.finishProgress();
									}
								}
							});
						}
					}
					// wait until the end
					queueLimitSemaphore.acquire(100);
					final long endTime = System.currentTimeMillis();
					final Duration duration = new Duration(startTime, endTime);
					final StringBuffer buf = new StringBuffer("Refresh-Time: ");
					PeriodFormat.wordBased().getPrinter().printTo(buf, duration.toPeriod(), Locale.getDefault());
					buf.append(", ");
					buf.append(imageCount.intValue());
					buf.append(" Images");
					log.info(buf.toString());
					updateStatistics(countByTag);
				} catch (final InterruptedException e) {
					log.info("cache refresh interrupted");
				}
				return null;
			}
		});
	}

	@Override
	public void registerClient(final String albumId, final String clientId) {
		final String albumPath = Util.decodeStringOfUrl(albumId);
		updateMeta("added " + albumPath + " to client " + clientId, new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				final Map<String, StorageData> albumPerStorage = store.getArchiveData(ReadPolicy.READ_OR_CREATE).getStorages();

				final Collection<String> albumCollection;
				if (albumPerStorage.containsKey(clientId)) {
					albumCollection = albumPerStorage.get(clientId).getAlbumList();
				} else {
					final StorageData storageData = new StorageData();
					albumCollection = storageData.getAlbumList();
					albumPerStorage.put(clientId, storageData);
				}
				if (!albumCollection.contains(albumPath)) {
					albumCollection.add(albumPath);
				}
				return null;
			}
		});
	}

	@Override
	public void setArchiveName(final String archiveName) {
		updateMeta("ArchiveName upated", new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				store.getArchiveData(ReadPolicy.READ_OR_CREATE).setArchiveName(archiveName);
				return null;
			}
		});
		executorService.submit(new Runnable() {

			@Override
			public void run() {
				pollCurrentKnownPeers();
			}
		});
	}

	@Override
	public synchronized void setBaseDir(final File baseDir) {
		if (Objects.equals(this.baseDir, baseDir)) {
			return;
		}
		this.baseDir = baseDir;
		store = new LocalStore(new File(baseDir, META_REPOSITORY));
		loadedAlbums.clear();
		lastLoadedDate.set(0);
		if (preferences != null) {
			preferences.put(ALBUM_PATH_PREFERENCE, baseDir.getAbsolutePath());
		}
		loadMetaConfig();
		if (executorService != null) {
			executorService.submit(new Runnable() {

				@Override
				public void run() {
					refreshCache(false);
				}
			});
		}
	}

	public void setExecutorService(final ScheduledExecutorService executorService) {
		this.executorService = executorService;
	}

	@Override
	public void setImportBaseDir(final File importBaseDir) {
		if (ObjectUtils.equals(this.importBaseDir, importBaseDir)) {
			return;
		}
		this.importBaseDir = importBaseDir;
		if (importBaseDir != null && executorService != null) {
			if (fileWatcher != null) {
				fileWatcher.close();
				fileWatcher = createFileWatcher();
			}
		}
		if (preferences != null) {
			preferences.put(IMPORT_BASE_PATH_REFERENCE, importBaseDir.getAbsolutePath());
		}
	}

	@Override
	public void setInstanceName(final String instanceName) {
		if (ObjectUtils.equals(this.instanceName, instanceName)) {
			return;
		}
		this.instanceName = instanceName;
		final File outFile = makeClientIdFile();
		try {
			@Cleanup
			final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "utf-8"));
			writer.println(instanceName);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot write instance name to " + outFile, e);
		}
	}

	@PreDestroy
	private void shutdownDnsListener() throws IOException {
		if (jmmDNS != null) {
			jmmDNS.close();
			jmmDNS = null;
		}
	}

	@PreDestroy
	private void shutdownFileWatcher() {
		if (fileWatcher != null) {
			fileWatcher.close();
		}
	}

	/**
	 * Start scheduling automatically after initializing
	 */
	@PostConstruct
	protected void startScheduling() {
		executorService.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				try {
					log.info("Start refresh Cache");
					refreshCache(false);
					log.info("Start refresh thumbnails");
					final Collection<Album> albums = new ArrayList<Album>(loadAlbums(true).values());
					final Semaphore thumbnailSemaphore = new Semaphore(10);
					for (final Album album : albums) {
						for (final AlbumImage image : album.listImages().values()) {
							thumbnailSemaphore.acquire();
							executorService.submit(new Runnable() {

								@Override
								public void run() {
									try {
										image.getThumbnail(ThumbnailSize.BIG);
									} finally {
										thumbnailSemaphore.release();
									}
								}
							});
						}
					}
					// wait for all threads
					thumbnailSemaphore.acquire(10);
					log.info("Finidhed refreshing thumbnails");
				} catch (final Throwable t) {
					log.warn("Exception while refreshing thumbnails", t);
				}
			}
		}, 1, 15, TimeUnit.MINUTES);
	}

	private void syncExternal(final File path, final boolean bare) {
		try {
			final String localName = getInstanceName();
			final String remoteName = readClientId(path, bare);
			if (remoteName == null) {
				log.warn("No valid client-id at " + path);
				return;
			}
			final File remoteMetaDir = new File(path, makeRepositoryDirectoryName(bare, META_REPOSITORY));
			final boolean metaModified = repositoryService.sync(metaGit, remoteMetaDir, localName, remoteName, bare, metaRwLock);
			if (metaModified) {
				loadMetaConfig();
			}
			final ArchiveData remoteConfig = bare ? store.getArchiveData(ReadPolicy.READ_ONLY) : loadMetaConfigFile(new File(remoteMetaDir, "config.json"));
			final Collection<File> existingAlbumsOnExternalDisk = findAlbums(path, bare);
			final Map<String, File> existingRemoteDirectories = new HashMap<>();
			final int basePathLength = path.getAbsolutePath().length() + 1;
			for (final File file : existingAlbumsOnExternalDisk) {
				final String relativeName = cleanAlbumName(bare, file.getAbsolutePath().substring(basePathLength));
				if (relativeName == null) {
					continue;
				}
				if (relativeName.equals(META_REPOSITORY)) {
					continue;
				}
				existingRemoteDirectories.put(relativeName, file);
			}
			final Map<String, Album> existingLocalAlbums = new HashMap<>();
			for (final Album album : loadAlbums(true).values()) {
				existingLocalAlbums.put(album.getName(), album);
			}
			final Collection<String> albumsToSync = evaluateRepositoriesToSync(localName, existingRemoteDirectories.keySet(), store.getArchiveData(ReadPolicy.READ_ONLY));
			albumsToSync.addAll(evaluateRepositoriesToSync(remoteName, existingLocalAlbums.keySet(), remoteConfig));
			@Cleanup
			final ProgressHandler progress = stateManager.newProgress(albumsToSync.size(), ProgressType.SYNC_LOCAL_DISC, remoteName);
			final Collection<Callable<String>> tasks = new ArrayList<>(albumsToSync.size());
			final AtomicLong syncedSize = new AtomicLong(0);
			for (final String albumName : albumsToSync) {
				tasks.add(new Callable<String>() {

					@Override
					public String call() throws Exception {
						try {
							@Cleanup
							final Closeable albumStep = progress.notfiyProgress(albumName);
							final Album localAlbumForRemote = existingLocalAlbums.get(albumName);
							final File remoteDir = existingRemoteDirectories.get(albumName);
							if (localAlbumForRemote == null) {
								if (remoteDir != null) {
									final File albumDir = new File(getBaseDir(), albumName);
									if (albumDir.exists()) {
										return null;
									}
									try {
										final Album album = appendAlbum(loadedAlbums, albumDir, remoteDir.toURI().toString(), remoteName);
										if (albumsToSync.contains(album.getName())) {
											syncedSize.addAndGet(album.getRepositorySize());
											if (!bare) {
												for (final AlbumImage albumEntry : album.listImages().values()) {
													syncedSize.addAndGet(albumEntry.getAllFilesSize());
												}
											}
										}
										return makeAlbumId(album.getNameComps());
									} catch (final Exception e) {
										log.warn("Cannot read Repository " + path, e);
										// cleanup failed repository
										for (final File file : FileUtils.listFiles(albumDir, null, true)) {
											file.setWritable(true, false);
										}
										FileUtils.deleteDirectory(albumDir);
									}
								}
							} else {
								localAlbumForRemote.sync(new File(path, makeRepositoryDirectoryName(bare, albumName)), localName, remoteName, bare);
								return makeAlbumId(localAlbumForRemote.getNameComps());
							}
						} catch (final IOException ex) {
							throw new RuntimeException("Cannot synchronize " + albumName + " to " + path, ex);
						}
						return null;
					}
				});
			}
			final Set<String> reLoadedAlbums = new HashSet<String>();
			for (final Future<String> future : syncExecutorService.invokeAll(tasks)) {
				try {
					final String albumId = future.get();
					if (albumId != null) {
						reLoadedAlbums.add(albumId);
					}
				} catch (final Exception ex) {
					log.warn("Canot sync Album to " + path, ex);
				}
			}
			loadedAlbums.keySet().retainAll(reLoadedAlbums);

			final long bytesAvailable = (path.getFreeSpace() + syncedSize.get());
			final int gBytesAvailable = (int) (bytesAvailable / 1024 / 1024 / 1024);

			updateMeta("available size of " + remoteName + " updated", new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					final ArchiveData archiveData = store.getArchiveData(ReadPolicy.READ_OR_CREATE);
					final Map<String, StorageData> storages = archiveData.getStorages();
					final StorageData storageMeta;
					if (storages.containsKey(remoteName)) {
						storageMeta = storages.get(remoteName);
					} else {
						storageMeta = new StorageData();
						storageMeta.getAlbumList().addAll(albumsToSync);
						storages.put(remoteName, storageMeta);
					}
					storageMeta.setGBytesAvailable(gBytesAvailable);
					return null;
				}
			});
		} catch (final Throwable t) {
			log.warn("Cannot sync with " + path, t);
		}
	}

	@Override
	public void unRegisterClient(final String albumId, final String clientId) {
		final String albumPath = Util.decodeStringOfUrl(albumId);
		updateMeta("removed " + albumPath + " from client " + clientId, new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				final Map<String, StorageData> albumPerStorage = store.getArchiveData(ReadPolicy.READ_OR_CREATE).getStorages();

				if (!albumPerStorage.containsKey(clientId)) {
					return null;
				}
				final Collection<String> albumCollection = albumPerStorage.get(clientId).getAlbumList();
				if (albumCollection.contains(albumPath)) {
					albumCollection.remove(albumPath);
				}
				return null;
			}
		});
	}

	private void updateAllRepositories(final Collection<URI> collection) {
		// @Cleanup
		// final ProgressHandler peerServerProgressHandler =
		// stateManager.newProgress(collection.size(),
		// ProgressType.ITERATE_OVER_SERVERS,
		// "Polling remote Servers");
		for (final URI peerServerUri : collection) {
			final ResponseEntity<PingResponse> responseEntity = ping(peerServerUri);
			if (!responseEntity.hasBody()) {
				continue;
			}
			final PingResponse pingResponse = responseEntity.getBody();
			final String remoteHost = peerServerUri.getHost();
			// peerServerProgressHandler.notfiyProgress(pingResponse.getServerName());
			final int remotePort = pingResponse.getGitPort();
			final ResponseEntity<AlbumList> albumListEntity = restTemplate.getForEntity(peerServerUri.resolve("albums.json"), AlbumList.class);
			if (!albumListEntity.hasBody()) {
				continue;
			}
			try {
				final boolean metaUpdated = repositoryService.pull(	metaGit,
																														new URI("git", null, remoteHost, remotePort, "/.meta", null, null).toASCIIString(),
																														pingResponse.getServerName());
				if (metaUpdated) {
					loadMetaConfig();
				}
			} catch (final Throwable e) {
				log.error("Cannot pull remote repository from " + remoteHost, e);
			}
			final AlbumList remoteAlbumList = albumListEntity.getBody();
			final Map<String, Album> localAlbums = listAlbums();
			final Collection<AlbumEntry> albumNames = remoteAlbumList.getAlbumNames();
			final Map<String, AlbumEntry> remoteAlbums = new HashMap<>();
			for (final AlbumEntry remoteAlbum : albumNames) {
				remoteAlbums.put(remoteAlbum.getName(), remoteAlbum);
			}
			@Cleanup
			final ProgressHandler albumProgress = stateManager.newProgress(albumNames.size(), ProgressType.SYNC_REMOTE_SERVER, pingResponse.getServerName());
			final Collection<String> repositoriesToSync = evaluateRepositoriesToSync(getArchiveName(), remoteAlbums.keySet(), store.getArchiveData(ReadPolicy.READ_ONLY));
			for (final String albumName : repositoriesToSync) {
				syncExecutorService.submit(new Runnable() {

					@Override
					public void run() {
						try {
							// ping to check if the server is staying online
							final ResponseEntity<PingResponse> responseEntity = ping(peerServerUri);
							if (!responseEntity.hasBody()) {
								return;
							}
							final String remoteHost = peerServerUri.getHost();
							final int remotePort = pingResponse.getGitPort();
							final AlbumEntry album = remoteAlbums.get(albumName);
							final Album localAlbumForRemote = localAlbums.get(album.getId());
							@Cleanup
							final Closeable albumStep = albumProgress.notfiyProgress(albumName);
							final String remoteUri = new URI("git", null, remoteHost, remotePort, "/" + album.getId(), null, null).toASCIIString();
							if (localAlbumForRemote == null) {
								appendAlbum(loadedAlbums, new File(getBaseDir(), albumName), remoteUri, pingResponse.getServerName());
							} else {
								localAlbumForRemote.pull(remoteUri, pingResponse.getServerName());
							}
						} catch (final Throwable e) {
							log.warn("Cannot sync with " + remoteHost, e);
						}
					}
				});
			}
		}
	}

	private synchronized <T> T updateMeta(final String message, final Callable<T> callable) {
		try {
			final T result = store.callInTransaction(callable);
			commitNeededFiles(message, metaGit);
			return result;
		} catch (final Throwable e) {
			throw new RuntimeException("Cannot update Metadata", e);
		}
	}

	@Override
	public void updateMetadata(final String albumId, final Collection<Mutation> updateEntries) {
		final Map<String, Album> albums = loadAlbums(false);
		final Album foundAlbum = albums.get(albumId);
		if (foundAlbum == null) {
			return;
		}
		updateMeta("Metadata updated", new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				final Map<String, StorageData> albumPerStorage = store.getArchiveData(ReadPolicy.READ_OR_CREATE).getStorages();
				for (final Mutation mutation : updateEntries) {
					if (mutation instanceof MetadataMutation) {
						final MetadataMutation metadataMutation = (MetadataMutation) mutation;
						final RevCommit latestMetaCommit = findLatestMetaCommit();
						// if (metadataMutation.getMetadataVersion().equals(latestMetaCommit.getId().name())) {
						if (metadataMutation instanceof StorageMutation) {
							final StorageMutation mutationEntry = (StorageMutation) metadataMutation;
							final String storageName = Util.decodeStringOfUrl(mutationEntry.getStorage());
							final String albumPath = Util.decodeStringOfUrl(albumId);
							final Collection<String> albumCollection = albumPerStorage.get(storageName).getAlbumList();

							if (!albumPerStorage.containsKey(storageName)) {
								continue;
							}
							switch (mutationEntry.getMutation()) {
							case ADD:
								albumCollection.add(albumPath);
								break;
							case REMOVE:
								albumCollection.remove(albumPath);
								break;
							}
						}
						// }
					}
				}
				return null;
			}
		});
		foundAlbum.updateMetadata(updateEntries);
	}

	private void updateStatistics(final ConcurrentMap<String, AtomicInteger> countByTag) throws IOException, JsonGenerationException, JsonMappingException {
		final StorageStatistics statistics = new StorageStatistics();
		for (final Entry<String, AtomicInteger> keywordEntry : countByTag.entrySet()) {
			statistics.getKeywordCount().put(keywordEntry.getKey(), Integer.valueOf(keywordEntry.getValue().intValue()));
		}
		mapper.writer().with(new DefaultPrettyPrinter()).writeValue(new File(getServercacheDir(), "statistics.json"), statistics);
	}

	@Override
	public void waitForAlbums() {
		loadAlbums(true);
	}
}
