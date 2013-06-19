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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
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
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import lombok.Cleanup;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.data.model.StorageEntry;
import ch.bergturbenthal.raoa.data.model.StorageList;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.data.model.mutation.StorageMutationEntry;
import ch.bergturbenthal.raoa.data.model.state.ProgressType;
import ch.bergturbenthal.raoa.data.util.ExecutorServiceUtil;
import ch.bergturbenthal.raoa.server.metadata.MetadataWrapper;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.model.ArchiveData;
import ch.bergturbenthal.raoa.server.model.StorageData;
import ch.bergturbenthal.raoa.server.model.StorageStatistics;
import ch.bergturbenthal.raoa.server.state.ProgressHandler;
import ch.bergturbenthal.raoa.server.state.StateManager;
import ch.bergturbenthal.raoa.server.util.ConcurrentUtil;
import ch.bergturbenthal.raoa.server.util.RepositoryService;
import ch.bergturbenthal.raoa.server.watcher.FileNotification;
import ch.bergturbenthal.raoa.server.watcher.FileWatcher;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class FileAlbumAccess implements AlbumAccess, StorageAccess, FileConfiguration, ArchiveConfiguration, FileNotification, ApplicationContextAware {
	private static final String ALBUM_PATH_PREFERENCE = "album_path";
	private static final String CLIENTID_FILENAME = ".clientid";
	private static final String IMPORT_BASE_PATH_REFERENCE = "import_base_path";
	private static final ObjectMapper mapper = new ObjectMapper().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
	private static final String META_CACHE = "cache";
	private static final String META_REPOSITORY = ".meta";

	private static final String SERVICE_TYPE = "_images._tcp.local.";
	private ApplicationContext applicationContext;
	private ArchiveData archiveData;
	private File baseDir;
	private final ConcurrentMap<String, Object> createAlbumLocks = new ConcurrentHashMap<String, Object>();
	@Autowired
	private ScheduledExecutorService executorService;

	private FileWatcher fileWatcher = null;
	private File importBaseDir;
	private final String instanceId = UUID.randomUUID().toString();
	private String instanceName;
	private final Object instanceNameLoadLock = new Object();
	private JmmDNS jmmDNS;
	private final AtomicLong lastLoadedDate = new AtomicLong(0);
	private Map<String, Album> loadedAlbums = new HashMap<>();
	private final Logger logger = LoggerFactory.getLogger(FileAlbumAccess.class);
	private Git metaGit;
	private Preferences preferences = null;;
	private final Semaphore refreshThumbnailsSemaphore = new Semaphore(1);
	@Autowired
	private RepositoryService repositoryService;
	private final RestTemplate restTemplate = new RestTemplate();
	private ExecutorService safeExecutorService;
	@Autowired
	private StateManager stateManager;
	private final ExecutorService syncExecutorService = Executors.newFixedThreadPool(	Runtime.getRuntime().availableProcessors(),
																																										new CustomizableThreadFactory("sync-thread"));

	private final Semaphore updateAlbumListSemaphore = new Semaphore(1);

	@Override
	public Collection<String> clientsPerAlbum(final String albumId) {
		final String albumName = Util.decodeStringOfUrl(albumId);
		final HashSet<String> ret = new HashSet<String>();
		for (final Entry<String, StorageData> albumEntry : archiveData.getStorages().entrySet()) {
			if (albumEntry.getValue().getAlbumList().contains(albumName)) {
				ret.add(albumEntry.getKey());
			}
		}
		return ret;
	}

	@Override
	public synchronized Album createAlbum(final String[] pathNames) {
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

	@Override
	public Album getAlbum(final String albumId) {
		return listAlbums().get(albumId);
	}

	@Override
	public String getArchiveName() {
		return archiveData.getArchiveName();
	}

	@Override
	public File getBaseDir() {
		return baseDir;
	}

	@Override
	public String getCollectionId() {
		return archiveData.getArchiveName();
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

	@Override
	public Repository getMetaRepository() {
		return metaGit.getRepository();
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

	@Override
	public void importFiles(final File importDir) {

		try {
			if (!importDir.getAbsolutePath().startsWith(importBaseDir.getAbsolutePath())) {
				logger.error("Secutity-Error: Not allowed to read Images from " + importDir + " (Import-Path is " + importBaseDir + ")");
				return;
			}
			final HashSet<Album> modifiedAlbums = new HashSet<Album>();
			final SortedMap<Date, Album> importAlbums = new TreeMap<Date, Album>();
			for (final Album album : loadAlbums(true).values()) {
				final Date beginDate = album.getAutoAddBeginDate();
				if (beginDate != null) {
					importAlbums.put(beginDate, album);
				}
			}
			for (final Entry<Date, Album> album : importAlbums.entrySet()) {
				logger.info(album.getValue() + ", Date: " + album.getKey());
			}
			final Collection<File> deleteFiles = new ArrayList<File>();
			final Collection<File> importCandicates = collectImportFiles(importDir);
			for (final File file : importCandicates) {
				try {
					// logger.info("Read: " + file.getName());
					final Metadata metadata = ImageMetadataReader.readMetadata(file);
					if (metadata == null) {
						continue;
					}
					final Date createDate = new MetadataWrapper(metadata).readCreateDate();
					if (createDate == null) {
						// skip images without creation date
						continue;
					}
					final SortedMap<Date, Album> entriesBeforeDate = importAlbums.headMap(createDate);
					if (entriesBeforeDate.isEmpty()) {
						// no matching album found
						continue;
					}
					final Album album = entriesBeforeDate.get(entriesBeforeDate.lastKey());
					// logger.info(" ->" + album.getName());
					if (album.importImage(file, createDate)) {
						modifiedAlbums.add(album);
						logger.debug("image " + file + " imported successfully to " + album.getName());
						deleteFiles.add(file);
					} else {
						logger.warn("Could not import image " + file);
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
					logger.error("Cannot delete File " + file);
				}
			}
		} finally {
			refreshCache(true);
		}
	}

	@PostConstruct
	public void initExecutorService() {
		safeExecutorService = ExecutorServiceUtil.wrap(executorService);
	}

	@Override
	public Map<String, Album> listAlbums() {
		return loadAlbums(false);
	}

	@Override
	public synchronized StorageList listKnownStorage() {
		final StorageList storageList = new StorageList();
		final Map<String, StorageData> storages = archiveData.getStorages();
		for (final Entry<String, StorageData> storageEntry : storages.entrySet()) {
			final String name = storageEntry.getKey();
			final StorageEntry entry = new StorageEntry();
			entry.setStorageName(name);
			entry.setStorageId(Util.encodeStringForUrl(name));
			final StorageData storageData = storageEntry.getValue();
			final int mBytesAvailable = storageData.getMBytesAvailable();
			if (mBytesAvailable != Integer.MAX_VALUE) {
				entry.setMBytesAvailable((long) mBytesAvailable);
			}
			for (final String albumName : storageData.getAlbumList()) {
				entry.getAlbumList().add(Util.encodeStringForUrl(albumName));
			}
			entry.setTakeAllRepositories(storageData.isTakeAllRepositories());
			storageList.getClients().add(entry);
		}
		try {
			final Iterator<RevCommit> log = metaGit.log().setMaxCount(1).call().iterator();
			if (log.hasNext()) {
				final RevCommit currentCommit = log.next();
				storageList.setVersion(currentCommit.getId().name());
				storageList.setLastModified(new Date(currentCommit.getCommitTime() * 1000));
			}
		} catch (final GitAPIException e) {
			throw new RuntimeException("Cannot read commit-log", e);
		}
		return storageList;
	}

	public ArchiveData loadMetaConfigFile(final File configFile) {
		try {
			final ArchiveData readValue = mapper.readValue(configFile, ArchiveData.class);
			return readValue;
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read meta-config from " + configFile, e);
		}
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

	@Override
	public synchronized void registerClient(final String albumId, final String clientId) {
		final String albumPath = Util.decodeStringOfUrl(albumId);
		final Map<String, StorageData> albumPerStorage = archiveData.getStorages();

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
			updateMeta("added " + albumPath + " to client " + clientId);
		}
	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void setArchiveName(final String archiveName) {
		if (StringUtils.equals(archiveData.getArchiveName(), archiveName)) {
			return;
		}
		archiveData.setArchiveName(archiveName);
		updateMeta("ArchiveName upated");
		executorService.submit(new Runnable() {

			@Override
			public void run() {
				pollCurrentKnownPeers();
			}
		});
	}

	@Override
	public synchronized void setBaseDir(final File baseDir) {
		if (ObjectUtils.equals(this.baseDir, baseDir)) {
			return;
		}
		this.baseDir = baseDir;
		loadedAlbums = new HashMap<>();
		lastLoadedDate.set(0);
		if (preferences != null) {
			preferences.put(ALBUM_PATH_PREFERENCE, baseDir.getAbsolutePath());
			flushPreferences();
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
			flushPreferences();
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

	@Override
	public void unRegisterClient(final String albumId, final String clientId) {
		final String albumPath = Util.decodeStringOfUrl(albumId);
		final Map<String, StorageData> albumPerStorage = archiveData.getStorages();

		if (!albumPerStorage.containsKey(clientId)) {
			return;
		}
		final Collection<String> albumCollection = albumPerStorage.get(clientId).getAlbumList();
		if (albumCollection.contains(albumPath)) {
			albumCollection.remove(albumPath);
			updateMeta("added " + albumPath + " to client " + clientId);
		}
	}

	@Override
	public void updateMetadata(final String albumId, final Collection<Mutation> updateEntries) {
		final Map<String, Album> albums = loadAlbums(false);
		final Album foundAlbum = albums.get(albumId);
		if (foundAlbum == null) {
			return;
		}
		for (final Mutation mutation : updateEntries) {
			if (mutation instanceof StorageMutationEntry) {
				final StorageMutationEntry mutationEntry = (StorageMutationEntry) mutation;
				if (!mutationEntry.getAlbumLastModified().equals(foundAlbum.getLastModified())) {
					continue;
				}
				switch (mutationEntry.getMutation()) {
				case ADD:
					registerClient(albumId, mutationEntry.getStorage());
					break;
				case REMOVE:
					unRegisterClient(albumId, mutationEntry.getStorage());
					break;
				}
			}
		}

		foundAlbum.updateMetadata(updateEntries);
	}

	@Override
	public void waitForAlbums() {
		loadAlbums(true);
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
					refreshCache(false);
				} catch (final Throwable t) {
					logger.warn("Exception while refreshing thumbnails", t);
				}
			}
		}, 60, 2 * 60, TimeUnit.MINUTES);
	}

	private Album appendAlbum(final Map<String, Album> albumMap, final File albumDir, final String remoteUri, final String serverName) {
		final String[] nameComps = evaluateNameComps(albumDir);
		final String albumId = makeAlbumId(nameComps);
		synchronized (getAlbumLock(albumDir)) {
			if (!albumMap.containsKey(albumId)) {
				final Album newAlbum = (Album) applicationContext.getBean("album", albumDir, nameComps, remoteUri, serverName);
				albumMap.put(albumId, newAlbum);
			}
		}
		return albumMap.get(albumId);
	}

	private String cleanAlbumName(final boolean bare, final String relativeDirectoryName) {
		if (bare) {
			if (!relativeDirectoryName.endsWith(".git")) {
				throw new RuntimeException(relativeDirectoryName + " not ends with .git");
			}
			return relativeDirectoryName.substring(0, relativeDirectoryName.length() - 4);
		}
		return relativeDirectoryName;
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

	private void configureFromPreferences() {
		if (baseDir == null) {
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

	private FileWatcher createFileWatcher() {
		return (FileWatcher) applicationContext.getBean("fileWatcher", importBaseDir);
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
					final File basePath = getBasePath();
					logger.debug("Load Repositories from: " + basePath);
					final int basePathLength = basePath.getAbsolutePath().length();
					final Collection<Future<?>> futures = new ArrayList<>();
					for (final File albumDir : findAlbums(basePath, false)) {
						futures.add(safeExecutorService.submit(new Runnable() {

							@Override
							public void run() {
								logger.debug("Load Repository " + albumDir);
								final String relativePath = albumDir.getAbsolutePath().substring(basePathLength + 1);
								if (relativePath.equals(META_REPOSITORY)) {
									return;
								}
								try {
									appendAlbum(ret, albumDir, null, null);
									stateManager.clearException(relativePath);
								} catch (final BeanCreationException ex) {
									stateManager.recordException(relativePath, ex);
								}
							}
						}));
					}
					for (final Future<?> future : futures) {
						future.get();
					}
					lastLoadedDate.set(System.currentTimeMillis());
					loadedAlbums = ret;
					long archiveSize = 0;
					for (final Album album : ret.values()) {
						archiveSize += album.getRepositorySize();
						for (final AlbumImage image : album.listImages().values()) {
							archiveSize += image.getAllFilesSize();
						}
					}
					final long availableSize = archiveSize + baseDir.getFreeSpace();
					final int availableMBytes = (int) (availableSize / 1024 / 1024);
					if (archiveData.getStorages() == null) {
						archiveData.setStorages(new HashMap<String, StorageData>());
					}
					final Map<String, StorageData> storages = archiveData.getStorages();
					final String storageName = getInstanceName();
					if (storages.containsKey(storageName)) {
						storages.get(storageName).setMBytesAvailable(availableMBytes);
					} else {
						final StorageData storageData = new StorageData();
						for (final Album album : ret.values()) {
							storageData.getAlbumList().add(album.getName());
						}
						storageData.setMBytesAvailable(availableMBytes);
						storages.put(storageName, storageData);
					}
					updateMeta("available size of " + storageName + " updated");
				} catch (final Throwable e) {
					if (forceWait) {
						throw new RuntimeException("Troubles while accessing resource " + baseDir, e);
					} else {
						logger.error("Troubles while accessing resource " + baseDir, e);
					}
				}
			}
		} finally {
			updateAlbumListSemaphore.release();
		}
	}

	private String[] evaluateNameComps(final File albumDir) {
		return albumDir.getAbsolutePath().substring(getBasePath().getAbsolutePath().length() + 1).split(File.pathSeparator);
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

	private void flushPreferences() {
		try {
			preferences.flush();
		} catch (final BackingStoreException e) {
			logger.warn("Cannot persist config", e);
		}
	}

	private Object getAlbumLock(final File albumFile) {
		final String key = albumFile.getAbsolutePath();
		if (createAlbumLocks.containsKey(key)) {
			return createAlbumLocks.get(key);
		}
		createAlbumLocks.putIfAbsent(key, new Object());
		return createAlbumLocks.get(key);

	}

	private File getBasePath() {
		return baseDir.getAbsoluteFile();
	}

	private File getConfigFile() {
		return new File(getMetaDir(), "config.json");
	}

	private File getMetaDir() {
		final File metaDir = new File(getBasePath(), META_REPOSITORY);
		if (!metaDir.exists()) {
			metaDir.mkdirs();
		}
		return metaDir;
	}

	private File getServercacheDir() {
		final File cacheDir = new File(getMetaDir(), ".servercache");
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
		return cacheDir;
	}

	@PostConstruct
	private void init() {
		configureFromPreferences();
		if (importBaseDir != null && executorService != null) {
			fileWatcher = createFileWatcher();
		}
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
				throw new RuntimeException("Cannot access to git-repository of " + baseDir, e);
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
		final File configFile = new File(metaDir, "config.json");
		if (!configFile.exists()) {
			archiveData = new ArchiveData();
			archiveData.setArchiveName(UUID.randomUUID().toString());
			updateMeta("config.json built");
		} else {
			archiveData = loadMetaConfigFile(configFile);
			// if (archiveData != null) {
			// final Map<String, Collection<String>> oldMap =
			// archiveData.getAlbumPerStorage();
			// if (oldMap != null) {
			// final Map<String, StorageData> newStorages = archiveData.getStorages();
			// for (final Entry<String, Collection<String>> oldEntry :
			// oldMap.entrySet()) {
			// final StorageData newStorage = new StorageData();
			// newStorage.getAlbumList().addAll(oldEntry.getValue());
			// newStorages.put(oldEntry.getKey(), newStorage);
			// }
			// archiveData.setAlbumPerStorage(null);
			// }
			// updateMeta("Structure migrated");
			// }
		}
	}

	private String makeAlbumId(final File albumDir) {
		return makeAlbumId(evaluateNameComps(albumDir));
	}

	private String makeAlbumId(final String[] nameComps) {
		return Util.encodeStringForUrl(StringUtils.join(nameComps, "/"));
	}

	private File makeClientIdFile() {
		return new File(baseDir, CLIENTID_FILENAME);
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

	private ResponseEntity<PingResponse> ping(final URI uri) {
		return restTemplate.getForEntity(uri.resolve("ping.json"), PingResponse.class);
	}

	private void pollCurrentKnownPeers() {
		processFoundServices(jmmDNS.list(SERVICE_TYPE));
		refreshCache(true);
	}

	private synchronized void processFoundServices(final ServiceInfo[] services) {
		final Map<String, URI> foundPeers = new HashMap<String, URI>();
		for (final ServiceInfo serviceInfo : services) {
			final int peerPort = serviceInfo.getPort();
			final InetAddress[] addresses = serviceInfo.getInetAddresses();
			for (final InetAddress inetAddress : addresses) {
				if (inetAddress.isLinkLocalAddress()) {
					continue;
				}
				try {
					final URI candidateUri = new URI("http", null, inetAddress.getHostAddress(), peerPort, "/rest/", null, null);
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
				} catch (final URISyntaxException e) {
					logger.warn("Cannot build URL for " + serviceInfo, e);
				} catch (final RestClientException e) {
					logger.warn("ping " + serviceInfo, e);
				}
			}
		}
		logger.info("Found peers: ");
		for (final Entry<String, URI> peerEntry : foundPeers.entrySet()) {
			logger.info(" - " + peerEntry.getKey() + ": " + peerEntry.getValue());
		}
		updateAllRepositories(foundPeers.values());
	}

	private void readLocalSettingsFromPreferences() {
		setBaseDir(new File(preferences.get(ALBUM_PATH_PREFERENCE, new File(System.getProperty("user.home"), "images").getAbsolutePath())));
		setImportBaseDir(new File(preferences.get(IMPORT_BASE_PATH_REFERENCE, "nowhere")));
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
					final Collection<Album> albums = loadAlbums(wait).values();
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
										image.getThumbnail();
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
					logger.info(buf.toString());
					updateStatistics(countByTag);
				} catch (final InterruptedException e) {
					logger.info("cache refresh interrupted");
				}
				return null;
			}
		});
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

	private void syncExternal(final File path, final boolean bare) {
		try {
			final String localName = getInstanceName();
			final String remoteName = readClientId(path, bare);
			if (remoteName == null) {
				logger.warn("No valid client-id at " + path);
				return;
			}
			final File remoteMetaDir = new File(path, makeRepositoryDirectoryName(bare, META_REPOSITORY));
			final boolean metaModified = repositoryService.sync(metaGit, remoteMetaDir, localName, remoteName, bare);
			if (metaModified) {
				loadMetaConfig();
			}
			final ArchiveData remoteConfig = bare ? archiveData : loadMetaConfigFile(new File(remoteMetaDir, "config.json"));
			final Collection<File> existingAlbumsOnExternalDisk = findAlbums(path, bare);
			final Map<String, File> existingRemoteDirectories = new HashMap<>();
			final int basePathLength = path.getAbsolutePath().length() + 1;
			for (final File file : existingAlbumsOnExternalDisk) {
				final String relativeName = cleanAlbumName(bare, file.getAbsolutePath().substring(basePathLength));
				if (relativeName.equals(META_REPOSITORY)) {
					continue;
				}
				existingRemoteDirectories.put(relativeName, file);
			}
			final Map<String, Album> existingLocalAlbums = new HashMap<>();
			for (final Album album : loadAlbums(true).values()) {
				existingLocalAlbums.put(album.getName(), album);
			}
			final Collection<String> albumsToSync = evaluateRepositoriesToSync(localName, existingRemoteDirectories.keySet(), archiveData);
			albumsToSync.addAll(evaluateRepositoriesToSync(remoteName, existingLocalAlbums.keySet(), remoteConfig));
			@Cleanup
			final ProgressHandler progress = stateManager.newProgress(albumsToSync.size(), ProgressType.SYNC_LOCAL_DISC, remoteName);
			final Collection<Callable<Void>> tasks = new ArrayList<>(albumsToSync.size());
			final AtomicLong syncedSize = new AtomicLong(0);
			for (final String albumName : albumsToSync) {
				tasks.add(new Callable<Void>() {

					@Override
					public Void call() throws Exception {
						try {
							@Cleanup
							final Closeable albumStep = progress.notfiyProgress(albumName);
							final Album localAlbumForRemote = existingLocalAlbums.get(albumName);
							final File remoteDir = existingRemoteDirectories.get(albumName);
							if (localAlbumForRemote == null) {
								if (remoteDir != null) {
									final File albumDir = new File(getBaseDir(), albumName);
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
									} catch (final Exception e) {
										logger.warn("Cannot read Repository " + path, e);
										// cleanup failed repository
										for (final File file : (Collection<File>) FileUtils.listFiles(albumDir, null, true)) {
											file.setWritable(true, false);
										}
										FileUtils.deleteDirectory(albumDir);
									}
								}
							} else {
								localAlbumForRemote.sync(new File(path, makeRepositoryDirectoryName(bare, albumName)), localName, remoteName, bare);
							}
						} catch (final IOException ex) {
							throw new RuntimeException("Cannot synchronize " + albumName + " to " + path, ex);
						}
						return null;
					}
				});
			}
			syncExecutorService.invokeAll(tasks);
			final long bytesAvailable = (path.getFreeSpace() + syncedSize.get());
			final int mBytesAvailable = (int) (bytesAvailable / 1024 / 1024);

			final Map<String, StorageData> storages = archiveData.getStorages();
			StorageData storageMeta;
			if (storages.containsKey(remoteName)) {
				storageMeta = storages.get(remoteName);
			} else {
				storageMeta = new StorageData();
				storageMeta.getAlbumList().addAll(albumsToSync);
				storages.put(remoteName, storageMeta);
			}
			storageMeta.setMBytesAvailable(mBytesAvailable);
			updateMeta("available size of " + remoteName + " updated");
		} catch (final Throwable t) {
			logger.warn("Cannot sync with " + path, t);
		}
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
				logger.error("Cannot pull remote repository from " + remoteHost, e);
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
			final Collection<String> repositoriesToSync = evaluateRepositoriesToSync(getArchiveName(), remoteAlbums.keySet(), archiveData);
			for (final String albumName : repositoriesToSync) {
				try {
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
					logger.error("Cannot sync with " + remoteHost, e);
				}
			}
		}
	}

	private synchronized void updateMeta(final String message) {
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(getConfigFile(), archiveData);
			metaGit.add().addFilepattern(".").call();
			if (!metaGit.status().call().isClean()) {
				metaGit.commit().setMessage(message).call();
			}
		} catch (final IOException e) {
			throw new RuntimeException("Cannot update Metadata", e);
		} catch (final GitAPIException e) {
			throw new RuntimeException("Cannot update Metadata", e);
		}
	}

	private void updateStatistics(final ConcurrentMap<String, AtomicInteger> countByTag) throws IOException, JsonGenerationException, JsonMappingException {
		final StorageStatistics statistics = new StorageStatistics();
		for (final Entry<String, AtomicInteger> keywordEntry : countByTag.entrySet()) {
			statistics.getKeywordCount().put(keywordEntry.getKey(), Integer.valueOf(keywordEntry.getValue().intValue()));
		}
		mapper.writer().withPrettyPrinter(new DefaultPrettyPrinter()).writeValue(new File(getServercacheDir(), "statistics.json"), statistics);
	}
}
