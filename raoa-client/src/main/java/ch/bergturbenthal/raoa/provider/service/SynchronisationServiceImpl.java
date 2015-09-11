package ch.bergturbenthal.raoa.provider.service;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Cleanup;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.TxMaker;
import org.mapdb.TxRollbackException;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import ch.bergturbenthal.raoa.client.util.CallbackUtil;
import ch.bergturbenthal.raoa.data.model.ArchiveMeta;
import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.data.model.StorageEntry;
import ch.bergturbenthal.raoa.data.model.mutation.AlbumMutation;
import ch.bergturbenthal.raoa.data.model.mutation.CaptionMutationEntry;
import ch.bergturbenthal.raoa.data.model.mutation.EntryMutation;
import ch.bergturbenthal.raoa.data.model.mutation.KeywordMutationEntry;
import ch.bergturbenthal.raoa.data.model.mutation.KeywordMutationEntry.KeywordMutation;
import ch.bergturbenthal.raoa.data.model.mutation.MetadataMutation;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.data.model.mutation.RatingMutationEntry;
import ch.bergturbenthal.raoa.data.model.mutation.StorageMutation;
import ch.bergturbenthal.raoa.data.model.mutation.TitleImageMutation;
import ch.bergturbenthal.raoa.data.model.mutation.TitleMutation;
import ch.bergturbenthal.raoa.data.model.state.Issue;
import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.Progress;
import ch.bergturbenthal.raoa.data.util.ExecutorServiceUtil;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.SortOrder;
import ch.bergturbenthal.raoa.provider.SortOrderEntry.Order;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;
import ch.bergturbenthal.raoa.provider.criterium.Value;
import ch.bergturbenthal.raoa.provider.map.BooleanFieldReader;
import ch.bergturbenthal.raoa.provider.map.FieldReader;
import ch.bergturbenthal.raoa.provider.map.MapperUtil;
import ch.bergturbenthal.raoa.provider.map.NotifyableCursor;
import ch.bergturbenthal.raoa.provider.map.NumericFieldReader;
import ch.bergturbenthal.raoa.provider.map.StringFieldReader;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumDto;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryIndex;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumEntryType;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumIndex;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMeta;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMeta.AlbumMetaBuilder;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumMutationData;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumState;
import ch.bergturbenthal.raoa.provider.service.discovery.JMDnsListener;
import ch.bergturbenthal.raoa.provider.service.discovery.ServerDiscoveryListener;
import ch.bergturbenthal.raoa.provider.service.discovery.ServerDiscoveryListener.ResultListener;
import ch.bergturbenthal.raoa.provider.state.ServerListActivity;
import ch.bergturbenthal.raoa.provider.util.LazyLoader;
import ch.bergturbenthal.raoa.provider.util.LazyLoader.Lookup;
import ch.bergturbenthal.raoa.provider.util.ObjectUtils;
import ch.bergturbenthal.raoa.provider.util.ReadOnlyIterableIndexedAccess;
import ch.bergturbenthal.raoa.provider.util.ThumbnailUriParser;
import ch.bergturbenthal.raoa.provider.util.ThumbnailUriParser.ThumbnailUriReceiver;

public class SynchronisationServiceImpl extends Service implements ResultListener, SynchronisationService {
	/**
	 * Class used for the client Binder. Because we know this service always runs in the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public SynchronisationService getService() {
			// Return this instance of LocalService so clients can call public methods
			return SynchronisationServiceImpl.this;
		}
	}

	private static class ThumbnailEntry {
		private boolean	confirmedByServer;
		private File		referencedFile;
	}

	private static interface TransactionCallable<V> {
		V call(final DataProvider provider);
	}

	private final static String	                                       SERVICE_TAG	           = "Synchronisation Service";
	private static final String	                                       THUMBNAIL_SUFFIX	       = ".thumbnail";

	// Binder given to clients
	private final IBinder	                                             binder	                 = new LocalBinder();

	private final AtomicReference<Map<String, ArchiveConnection>>	     connectionMap	         = new AtomicReference<Map<String, ArchiveConnection>>(Collections.<String, ArchiveConnection> emptyMap());
	private final ThreadLocal<DataProvider>	                           currentMapDbTransaction	= new ThreadLocal<DataProvider>();
	private final CursorNotification	                                 cursorNotifications	   = new CursorNotification();

	private File	                                                     dataDir;
	private ServerDiscoveryListener	                                   dnsListener;
	private ScheduledExecutorService	                                 executorService;
	private ScheduledFuture<?>	                                       fastUpdatePollingFuture;
	private final LruCache<String, Long>	                             idCache	               = new LruCache<String, Long>(100) {

		                                                                                           private final AtomicLong	idGenerator	= new AtomicLong(0);

		                                                                                           @Override
		                                                                                           protected Long create(final String key) {
			                                                                                           return idGenerator.incrementAndGet();
		                                                                                           }

	                                                                                           };
	private TxMaker	                                                   mapDB;
	private final int	                                                 NOTIFICATION	           = 0;

	private NotificationManager	                                       notificationManager;
	private final Semaphore	                                           pollServerSemaphore	   = new Semaphore(1);

	private final AtomicBoolean	                                       running	               = new AtomicBoolean(false);

	private ScheduledFuture<?>	                                       slowUpdatePollingFuture	= null;

	private File	                                                     tempDir;

	private final AtomicInteger	                                       tempFileId	             = new AtomicInteger();

	private LruCache<AlbumEntryIndex, ThumbnailEntry>	                 thumbnailCache;

	private File	                                                     thumbnailsSyncDir;

	private File	                                                     thumbnailsTempDir;

	private final Semaphore	                                           updateLockSempahore	   = new Semaphore(1);
	private final ConcurrentMap<String, ConcurrentMap<String, String>>	visibleAlbums	         = new ConcurrentHashMap<String, ConcurrentMap<String, String>>();
	private ExecutorService	                                           wrappedExecutorService;

	@Override
	public void createAlbumOnServer(final String serverId, final String fullAlbumName, final Date autoAddDate) {
		final ServerConnection serverConnection = getConnectionForServer(serverId);
		if (serverConnection == null) {
			return;
		}
		serverConnection.createAlbum(fullAlbumName, autoAddDate);
	}

	@Override
	public String getContenttype(final String archive, final String albumId, final String image) {
		return callInTransaction(new Callable<String>() {

			@Override
			public String call() throws Exception {
				final AlbumEntryDto entryDto = getCurrentDataProvider().getAlbumEntryMap().get(new AlbumEntryIndex(AlbumIndex.builder()
				                                                                                                             .archiveName(archive)
				                                                                                                             .albumId(albumId)
				                                                                                                             .build(), image));
				if (entryDto == null) {
					return null;
				}
				switch (entryDto.getEntryType()) {
				case IMAGE:
					return "image/jpeg";
				case VIDEO:
					return "video/mp4";
				default:
					return null;
				}
			}
		},
		                         true);
	}

	@Override
	public File getLoadedThumbnail(final String archiveName, final String albumId, final String albumEntryId) {
		// final long startTime = System.currentTimeMillis();
		// Log.i("Performance", "Start load Thumbnail " + archiveName + ":" + albumId + ":" + albumEntryId);
		try {
			final ThumbnailEntry thumbnailEntry = thumbnailCache.get(new AlbumEntryIndex(AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build(), albumEntryId));
			if (thumbnailEntry == null) {
				return null;
			}
			return thumbnailEntry.referencedFile;
		} finally {
			// Log.i("Performance", "Returned Thumbnail " + archiveName + ":" + albumId + ":" + albumEntryId + " in " + (System.currentTimeMillis() -
			// startTime) + " ms");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ch.bergturbenthal.raoa.provider.service.SynchronisationService#importFile(java.lang.String, byte[])
	 */
	@Override
	public void importFile(final String serverName, final String filename, final byte[] data) {

		final ServerConnection serverConnection = getConnectionForServer(serverName);
		serverConnection.importFile(filename, data);

	}

	@Override
	public void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints, final boolean withProgressUpdate) {
		for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
			Log.i(SERVICE_TAG, "Addr: " + inetSocketAddress);
		}
		final Map<String, Map<URL, PingResponse>> pingResponses = new HashMap<String, Map<URL, PingResponse>>();
		for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
			try {
				if (!inetSocketAddress.getAddress().isReachable(200)) {
					Log.i(SERVICE_TAG, "Ping to " + inetSocketAddress + " failed");
					continue;
				}
			} catch (final IOException e) {
				Log.w(SERVICE_TAG, "Cannot ping to " + inetSocketAddress);
				continue;
			}
			final URL url = makeUrl(inetSocketAddress);
			try {
				final PingResponse response = pingService(url);
				if (response != null) {
					if (pingResponses.containsKey(response.getArchiveId())) {
						pingResponses.get(response.getArchiveId()).put(url, response);
					} else {
						final Map<URL, PingResponse> map = new HashMap<URL, PingResponse>();
						map.put(url, response);
						pingResponses.put(response.getArchiveId(), map);
					}
				}
			} catch (final Throwable ex) {
				Log.e(SERVICE_TAG, "Exception while polling " + url, ex);
			}
		}
		final HashMap<String, ArchiveConnection> oldConnectionMap = new HashMap<String, ArchiveConnection>(connectionMap.get());
		final HashMap<String, ArchiveConnection> newConnectionMap = new HashMap<String, ArchiveConnection>();
		for (final Entry<String, Map<URL, PingResponse>> responseEntry : pingResponses.entrySet()) {
			final String archiveId = responseEntry.getKey();
			final ArchiveConnection connection = oldConnectionMap.containsKey(archiveId) ? oldConnectionMap.get(archiveId) : new ArchiveConnection(archiveId,
			                                                                                                                                       wrappedExecutorService);
			connection.updateServerConnections(responseEntry.getValue());
			newConnectionMap.put(archiveId, connection);
		}
		connectionMap.set(newConnectionMap);
		updateServerCursors();
		executorService.submit(new Runnable() {

			@Override
			public void run() {
				updateAlbumsOnDB();
			}
		});
		Log.i(SERVICE_TAG, pingResponses.toString());
	}

	@Override
	public IBinder onBind(final Intent arg0) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		executorService = Executors.newScheduledThreadPool(2, new ThreadFactory() {
			final AtomicInteger	nextThreadIndex	= new AtomicInteger(0);

			@Override
			public Thread newThread(final Runnable r) {
				return new Thread(r, "synchronisation-worker-" + nextThreadIndex.getAndIncrement());
			}
		});

		registerScreenOnOff();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		wrappedExecutorService = ExecutorServiceUtil.wrap(executorService);

		initServiceDiscovery();

		dataDir = new File(getFilesDir(), "data");
		final File mapdbFile = new File(dataDir, "mapdb");
		if (!mapdbFile.getParentFile().exists()) {
			mapdbFile.getParentFile().mkdirs();
		}
		mapDB = DBMaker.newFileDB(mapdbFile).makeTxMaker();
		// init db before first usage
		callInTransaction(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				return null;
			}
		}, false);

		// setup and clean temp-dir
		tempDir = new File(getCacheDir(), "temp");
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}
		for (final File file : tempDir.listFiles()) {
			file.delete();
		}

		// setup thumbnails-dir
		// temporary files
		thumbnailsTempDir = new File(getCacheDir(), "thumbnails");
		if (!thumbnailsTempDir.exists()) {
			thumbnailsTempDir.mkdirs();
		}
		// explicit synced thumbnails
		thumbnailsSyncDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "royalarchive");
		if (!thumbnailsSyncDir.exists()) {
			thumbnailsSyncDir.mkdirs();
		}
		// preload thumbnail-cache
		initThumbnailCache(2 * 1024 * 1024);

		executorService.schedule(new Runnable() {

			@Override
			public void run() {
				NetworkReceiver.notifyNetworkState(getApplicationContext());
			}
		}, 2, TimeUnit.SECONDS);
	}

	@Override
	public void onDestroy() {
		executorService.shutdownNow();
		notificationManager.cancel(NOTIFICATION);
		dnsListener.stopListening();
		stopSlowPolling();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		if (intent != null) {
			final ServiceCommand command = intent.getParcelableExtra("command");
			if (command != null) {
				switch (command) {
				case START:
					startRunning();
					break;
				case STOP:
					stopRunning();
					break;
				case POLL:
					pollServers();
					break;
				case SCREEN_ON:
					startFastPolling();
					break;
				case SCREEN_OFF:
					stopFastPolling();
					break;
				default:
					break;
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public Cursor readAlbumEntryKeywords(final String archiveName, final String albumName, final String[] projection, final Criterium criterium, final SortOrder order) {
		return callInTransaction(new Callable<Cursor>() {

			@Override
			public Cursor call() throws Exception {
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final BTreeMap<AlbumIndex, AlbumMeta> albumMetaMap = currentDataProvider.getAlbumMetadataMap();
				final BTreeMap<AlbumIndex, AlbumMutationData> albumMutationMap = currentDataProvider.getAlbumMutationDataMap();
				final AlbumIndex index = new AlbumIndex(albumName, archiveName);

				final AlbumMeta albumMeta = albumMetaMap.get(index);
				final Map<String, Integer> keywordCounts;
				if (albumMeta != null) {
					keywordCounts = applyKeywordMutations(albumMeta.getKeywordCounts(), albumMutationMap.get(index));
				} else {
					keywordCounts = Collections.emptyMap();
				}
				final Map<String, FieldReader<Entry<String, Integer>>> fieldReaders = new HashMap<String, FieldReader<Entry<String, Integer>>>();
				fieldReaders.put(Client.KeywordEntry.KEYWORD, new StringFieldReader<Map.Entry<String, Integer>>() {
					@Override
					public String getString(final Entry<String, Integer> value) {
						return value.getKey();
					}
				});
				fieldReaders.put(Client.KeywordEntry.COUNT, new NumericFieldReader<Map.Entry<String, Integer>>(Cursor.FIELD_TYPE_INTEGER) {

					@Override
					public Number getNumber(final Entry<String, Integer> value) {
						return value.getValue();
					}
				});
				return cursorNotifications.addAllAlbumCursor(MapperUtil.loadCollectionIntoCursor(keywordCounts.entrySet(), projection, fieldReaders, criterium, order));
			}
		}, true);

	}

	@Override
	public Cursor readAlbumEntryList(final String archiveName, final String albumId, final String[] projection, final Criterium criterium, final SortOrder order) {
		final AlbumIndex album = AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build();

		return callWithLongTransaction(new TransactionCallable<Cursor>() {

			@Override
			public Cursor call(final DataProvider provider) {
				final long startTime = System.currentTimeMillis();
				final AlbumMeta albumMeta = provider.getAlbumMetadataMap().get(album);
				if (albumMeta == null) {
					return makeCursorForAlbumEntries(Collections.<AlbumEntryIndex> emptyList(), projection, Collections.singleton(album), criterium, order, provider);
				}
				final ReadOnlyIterableIndexedAccess<AlbumEntryIndex> readOnlyIterableIndexedAccess = new ReadOnlyIterableIndexedAccess<AlbumEntryIndex>(provider.listEntriesByAlbum(album)
				                                                                                                                                                .iterator(),
				                                                                                                                                        albumMeta.getEntryCount());
				Log.i(SERVICE_TAG, "Iteration-Time: " + (System.currentTimeMillis() - startTime) + " " + albumMeta.getEntryCount());
				return makeCursorForAlbumEntries(readOnlyIterableIndexedAccess, projection, Collections.singleton(album), criterium, order, provider);
			}
		});
	}

	@Override
	public Cursor readAlbumList(final String[] projection, final Criterium criterium, final SortOrder order) {
		final Criterium visibleCriterium = Criterium.ge(Value.field(Client.Album.VISIBLE_SERVER_COUNT), Value.constant(Integer.valueOf(1)));
		final Criterium syncedCriterium = Criterium.eq(Value.field(Client.Album.SHOULD_SYNC), Value.constant(Integer.valueOf(1)));
		final Criterium combinedEnabledCriterium = Criterium.or(visibleCriterium, syncedCriterium);
		final Criterium queryCriterium;
		if (criterium == null) {
			queryCriterium = combinedEnabledCriterium;
		} else {
			queryCriterium = Criterium.and(combinedEnabledCriterium, criterium);
		}
		return makeCursorForAlbums(projection, queryCriterium, order);
	}

	@Override
	public Cursor readKeywordStatistics(final String[] projection, final Criterium criterium, final SortOrder order) {
		return callInTransaction(new Callable<Cursor>() {

			@Override
			public Cursor call() throws Exception {
				final Map<String, Integer> keywordCounts = new TreeMap<String, Integer>();
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final BTreeMap<AlbumIndex, AlbumMeta> currentAlbumMetaMap = currentDataProvider.getAlbumMetadataMap();
				final BTreeMap<AlbumIndex, AlbumMutationData> currentAlbumMutationMap = currentDataProvider.getAlbumMutationDataMap();
				for (final Entry<AlbumIndex, AlbumMeta> entry : currentAlbumMetaMap.entrySet()) {
					final AlbumMeta albumMeta = entry.getValue();
					if (albumMeta == null) {
						continue;
					}
					final Map<String, Integer> storedCounts = albumMeta.getKeywordCounts();
					final Map<String, Integer> albumKeywordCounts = applyKeywordMutations(storedCounts, currentAlbumMutationMap.get(entry.getKey()));
					for (final Entry<String, Integer> keywordEntry : albumKeywordCounts.entrySet()) {
						final Integer oldCount = keywordCounts.get(keywordEntry.getKey());
						final Integer albumCount = keywordEntry.getValue();
						if (albumCount == null || albumCount.intValue() <= 0) {
							continue;
						}
						if (oldCount == null) {
							keywordCounts.put(keywordEntry.getKey(), albumCount);
						} else {
							keywordCounts.put(keywordEntry.getKey(), Integer.valueOf(albumCount.intValue() + oldCount.intValue()));
						}
					}
				}
				final Map<String, FieldReader<Entry<String, Integer>>> fieldReaders = new HashMap<String, FieldReader<Entry<String, Integer>>>();
				fieldReaders.put(Client.KeywordEntry.KEYWORD, new StringFieldReader<Map.Entry<String, Integer>>() {
					@Override
					public String getString(final Entry<String, Integer> value) {
						return value.getKey();
					}
				});
				fieldReaders.put(Client.KeywordEntry.COUNT, new NumericFieldReader<Map.Entry<String, Integer>>(Cursor.FIELD_TYPE_INTEGER) {

					@Override
					public Number getNumber(final Entry<String, Integer> value) {
						return value.getValue();
					}
				});
				return cursorNotifications.addAllAlbumCursor(MapperUtil.loadCollectionIntoCursor(keywordCounts.entrySet(), projection, fieldReaders, criterium, order));
			}
		}, true);
	}

	@Override
	public Cursor readServerIssueList(final String serverId, final String[] projection, final Criterium criterium, final SortOrder order) {
		final ServerConnection serverConnection = getConnectionForServer(serverId);
		if (serverConnection == null) {
			return null;
		}
		final Collection<Issue> progressValues = new ArrayList<Issue>(serverConnection.getServerState().getIssues());

		final Map<String, String> mappedFields = new HashMap<String, String>();
		// mappedFields.put(Client.IssueEntry.CAN_ACK, "acknowledgable");
		mappedFields.put(Client.IssueEntry.ISSUE_ACTION_ID, "issueId");
		mappedFields.put(Client.IssueEntry.ALBUM_NAME, "albumName");
		mappedFields.put(Client.IssueEntry.ALBUM_DETAIL_NAME, "detailName");
		mappedFields.put(Client.IssueEntry.ISSUE_TIME, "issueTime");
		mappedFields.put(Client.IssueEntry.DETAILS, "details");
		mappedFields.put(Client.IssueEntry.ISSUE_TYPE, "type");

		final Map<String, FieldReader<Issue>> fieldReaders = MapperUtil.makeNamedFieldReaders(Issue.class, mappedFields);
		fieldReaders.put(Client.IssueEntry.ID, new NumericFieldReader<Issue>(Cursor.FIELD_TYPE_INTEGER) {
			@Override
			public Number getNumber(final Issue value) {
				return Long.valueOf(makeLongId(value.getIssueId()));
			}
		});
		fieldReaders.put(Client.IssueEntry.AVAILABLE_ACTIONS, new StringFieldReader<Issue>() {

			@Override
			public String getString(final Issue value) {
				return Client.IssueEntry.encodeActions(value.getAvailableActions());
			}
		});
		return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(progressValues, projection, fieldReaders, criterium, order));
	}

	@Override
	public Cursor readServerList(final String[] projection, final Criterium criterium, final SortOrder order) {
		final Map<String, ArchiveConnection> archives = connectionMap.get();

		final Collection<Pair<String, ServerConnection>> connections = new ArrayList<Pair<String, ServerConnection>>();
		for (final Entry<String, ArchiveConnection> archiveEntry : archives.entrySet()) {
			for (final ServerConnection server : archiveEntry.getValue().listServers().values()) {
				connections.add(new Pair<String, ServerConnection>(archiveEntry.getKey(), server));
			}
		}

		final Map<String, FieldReader<Pair<String, ServerConnection>>> fieldReaders = new HashMap<String, FieldReader<Pair<String, ServerConnection>>>();
		fieldReaders.put(Client.ServerEntry.ARCHIVE_NAME, new StringFieldReader<Pair<String, ServerConnection>>() {
			@Override
			public String getString(final Pair<String, ServerConnection> value) {
				return value.first;
			}
		});
		fieldReaders.put(Client.ServerEntry.SERVER_ID, new StringFieldReader<Pair<String, ServerConnection>>() {

			@Override
			public String getString(final Pair<String, ServerConnection> value) {
				return value.second.getInstanceId();
			}
		});
		fieldReaders.put(Client.ServerEntry.SERVER_NAME, new StringFieldReader<Pair<String, ServerConnection>>() {

			@Override
			public String getString(final Pair<String, ServerConnection> value) {
				return value.second.getServerName();
			}
		});
		fieldReaders.put(Client.ServerEntry.ID, new NumericFieldReader<Pair<String, ServerConnection>>(Cursor.FIELD_TYPE_INTEGER) {

			@Override
			public Number getNumber(final Pair<String, ServerConnection> value) {
				return Long.valueOf(makeLongId(value.second.getInstanceId()));
			}
		});

		return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(connections, projection, fieldReaders, criterium, order));

	}

	@Override
	public Cursor readServerProgresList(final String serverId, final String[] projection, final Criterium criterium, final SortOrder order) {
		final ServerConnection serverConnection = getConnectionForServer(serverId);
		if (serverConnection == null) {
			return null;
		}
		final Collection<Progress> progressValues = new ArrayList<Progress>(serverConnection.getServerState().getProgress());

		final Map<String, String> mappedFields = new HashMap<String, String>();
		mappedFields.put(Client.ProgressEntry.STEP_COUNT, "stepCount");
		mappedFields.put(Client.ProgressEntry.CURRENT_STEP_NR, "currentStepNr");
		mappedFields.put(Client.ProgressEntry.PROGRESS_DESCRIPTION, "progressDescription");
		mappedFields.put(Client.ProgressEntry.CURRENT_STATE_DESCRIPTION, "currentStepDescription");
		mappedFields.put(Client.ProgressEntry.PROGRESS_TYPE, "type");
		final Map<String, FieldReader<Progress>> fieldReaders = MapperUtil.makeNamedFieldReaders(Progress.class, mappedFields);
		fieldReaders.put(Client.ProgressEntry.ID, new NumericFieldReader<Progress>(Cursor.FIELD_TYPE_INTEGER) {

			@Override
			public Number getNumber(final Progress value) {
				return Long.valueOf(makeLongId(value.getProgressId()));
			}
		});
		return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(progressValues, projection, fieldReaders, criterium, order));
	}

	@Override
	public Cursor readSingleAlbum(final String archiveName, final String albumId, final String[] projection, final Criterium criterium, final SortOrder order) {
		final Criterium entryCriterium = Criterium.and(Criterium.eq(Value.field(Client.Album.ARCHIVE_NAME), Value.constant(archiveName)),
		                                               Criterium.eq(Value.field(Client.Album.ID), Value.constant(albumId)));
		final Criterium queryCriterium;
		if (criterium == null) {
			queryCriterium = entryCriterium;
		} else {
			queryCriterium = Criterium.and(entryCriterium, criterium);
		}
		return makeCursorForAlbums(projection, queryCriterium, order);
	}

	@Override
	public Cursor readSingleAlbumEntry(final String archiveName,
	                                   final String albumId,
	                                   final String archiveEntryId,
	                                   final String[] projection,
	                                   final Criterium criterium,
	                                   final SortOrder order) {
		return callWithLongTransaction(new TransactionCallable<Cursor>() {

			@Override
			public Cursor call(final DataProvider provider) {
				final AlbumIndex affectedAlbum = AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build();
				final AlbumEntryIndex albumEntryIndex = new AlbumEntryIndex(affectedAlbum, archiveEntryId);
				final AlbumEntryDto entryDto = provider.getAlbumEntryMap().get(albumEntryIndex);
				if (entryDto == null) {
					return makeCursorForAlbumEntries(Collections.<AlbumEntryIndex> emptyList(), projection, Collections.singleton(affectedAlbum), null, null, provider);
				}
				return makeCursorForAlbumEntries(Collections.singletonList(albumEntryIndex), projection, Collections.singleton(affectedAlbum), criterium, null, provider);
			}
		});
	}

	@Override
	public Cursor readStorages(final String[] projection, final Criterium criterium, final SortOrder order) {
		return callInTransaction(new Callable<Cursor>() {

			@Override
			public Cursor call() throws Exception {
				final HashSet<String> archives = new HashSet<String>();
				final DataProvider currentDataProvider = getCurrentDataProvider();
				for (final AlbumIndex albumEntry : currentDataProvider.getAlbumMetadataMap().keySet()) {
					archives.add(albumEntry.getArchiveName());
				}
				final Collection<Pair<String, StorageEntry>> storagePairs = new ArrayList<Pair<String, StorageEntry>>();
				for (final String archiv : archives) {
					final ArchiveMeta storageList = currentDataProvider.getArchiveMetaMap().get(archiv);
					if (storageList == null) {
						continue;
					}
					for (final StorageEntry entry : storageList.getClients()) {
						storagePairs.add(new Pair<String, StorageEntry>(archiv, entry));
					}
				}
				final Map<String, String> mappedFields = new HashMap<String, String>();
				mappedFields.put(Client.Storage.GBYTES_AVAILABLE, "gBytesAvailable");
				mappedFields.put(Client.Storage.STORAGE_NAME, "storageName");
				mappedFields.put(Client.Storage.TAKE_ALL_REPOSITORIES, "takeAllRepositories");
				mappedFields.put(Client.Storage.STORAGE_ID, "storageId");
				final Map<String, FieldReader<StorageEntry>> readers = MapperUtil.makeNamedFieldReaders(StorageEntry.class, mappedFields);
				final Map<String, FieldReader<Pair<String, StorageEntry>>> delegateFieldReaders = MapperUtil.delegateFieldReaders(readers,
				                                                                                                                  new Lookup<Pair<String, StorageEntry>, StorageEntry>() {
					                                                                                                                  @Override
					                                                                                                                  public StorageEntry get(final Pair<String, StorageEntry> key) {
						                                                                                                                  return key.second;
					                                                                                                                  }
				                                                                                                                  });
				delegateFieldReaders.put(Client.Storage.ARCHIVE_NAME, new StringFieldReader<Pair<String, StorageEntry>>() {

					@Override
					public String getString(final Pair<String, StorageEntry> value) {
						return value.first;
					}
				});
				return cursorNotifications.addStorageCursor(MapperUtil.loadCollectionIntoCursor(storagePairs, projection, delegateFieldReaders, criterium, order));
			}
		},
		                         true);
	}

	@Override
	public void resolveIssue(final String serverName, final String issueId, final IssueResolveAction action) {
		final ServerConnection serverConnection = getConnectionForServer(serverName);
		if (serverConnection == null) {
			// TODO show a message to user
			return;
		}
		// TODO show message if execution fails
		serverConnection.resolveIssue(issueId, action);
	}

	@Override
	public int updateAlbum(final String archiveName, final String albumId, final ContentValues values) {
		final AlbumIndex album = AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build();
		final Collection<AlbumEntryIndex> albumEntriesToClear = new ArrayList<AlbumEntryIndex>();
		final int updatedCount = callInTransaction(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final AlbumMeta albumMeta = currentDataProvider.getAlbumMetadataMap().get(album);
				if (albumMeta == null) {
					return Integer.valueOf(0);
				}
				final Collection<Mutation> newMutations = new ArrayList<Mutation>();
				cursorNotifications.notifySingleAlbumCursorChanged(album);
				// Handling of synchronization flag
				final Boolean shouldSync = values.getAsBoolean(Client.Album.SHOULD_SYNC);
				if (shouldSync != null) {
					final BTreeMap<AlbumIndex, AlbumState> albumStateMap = currentDataProvider.getAlbumStateMap();
					albumStateMap.putIfAbsent(album, AlbumState.builder().build());
					final AlbumState albumState = albumStateMap.get(album);
					if (albumState.isShouldSync() != shouldSync.booleanValue()) {
						albumStateMap.put(album, albumState.withShouldSync(shouldSync.booleanValue()).withSynced(albumState.isSynced() && shouldSync.booleanValue()));
						for (final AlbumEntryIndex albumEntryIndex : currentDataProvider.listEntriesByAlbum(album)) {
							albumEntriesToClear.add(albumEntryIndex);
						}
					}
				}

				// handling of thumbnail image
				final String thumbnailUri = values.getAsString(Client.Album.THUMBNAIL);
				if (thumbnailUri != null) {
					final String thumbnailId = ThumbnailUriParser.parseUri(Uri.parse(thumbnailUri), new ThumbnailUriReceiver<String>() {

						@Override
						public String execute(final String parsedArchiveName, final String parsedAlbumId, final String thumbnailId) {
							if (!parsedArchiveName.equals(archiveName)) {
								return null;
							}
							if (!parsedAlbumId.equals(albumId)) {
								return null;
							}
							return thumbnailId;
						}
					});
					if (thumbnailId != null) {
						final TitleImageMutation mutation = new TitleImageMutation();
						mutation.setAlbumLastModified(albumMeta.getLastModified());
						mutation.setTitleImage(thumbnailId);
						newMutations.add(mutation);
					}
				}

				// handling of album title
				final String title = values.getAsString(Client.Album.TITLE);
				if (title != null) {
					final TitleMutation mutation = new TitleMutation();
					mutation.setAlbumLastModified(albumMeta.getLastModified());
					mutation.setTitle(title);
					newMutations.add(mutation);
				}
				final String newStorages = values.getAsString(Client.Album.STORAGES);
				if (newStorages != null) {
					final ArchiveMeta archiveMeta = currentDataProvider.getArchiveMetaMap().get(album.getArchiveName());
					final String archiveMetaVersion = archiveMeta == null ? "undefined" : archiveMeta.getVersion();
					final Collection<String> existingStorages = findStoragesOfAlbum(album);
					final Collection<String> newStoragesCollection = Client.Album.decodeStorages(newStorages);
					for (final String newStorage : newStoragesCollection) {
						if (!existingStorages.contains(newStorage)) {
							final StorageMutation entry = new StorageMutation();
							entry.setMetadataVersion(archiveMetaVersion);
							entry.setMutation(StorageMutation.Mutation.ADD);
							entry.setStorage(newStorage);
							newMutations.add(entry);
						}
					}
					for (final String existingStorage : existingStorages) {
						if (!newStoragesCollection.contains(existingStorage)) {
							final StorageMutation entry = new StorageMutation();
							entry.setMetadataVersion(archiveMetaVersion);
							entry.setMutation(StorageMutation.Mutation.REMOVE);
							entry.setStorage(existingStorage);
							newMutations.add(entry);
						}
					}
				}
				if (!newMutations.isEmpty()) {
					final AlbumMutationData albumMutationData = currentDataProvider.getAlbumMutationDataMap().get(album);
					final ArrayList<Mutation> mutations = albumMutationData == null ? new ArrayList<Mutation>() : new ArrayList<Mutation>(albumMutationData.getMutations());
					mutations.addAll(newMutations);
					mutations.trimToSize();
					currentDataProvider.getAlbumMutationDataMap().put(album, new AlbumMutationData(mutations));
				}
				return Integer.valueOf(1);
			}

		}, false).intValue();
		for (final AlbumEntryIndex entryId : albumEntriesToClear) {
			thumbnailCache.remove(entryId);
		}
		return updatedCount;
	}

	@Override
	public int updateAlbumEntry(final String archiveName, final String albumId, final String albumEntryId, final ContentValues values) {
		final AlbumIndex album = AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build();
		return callInTransaction(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final AlbumEntryDto albumEntryDto = currentDataProvider.getAlbumEntryMap().get(new AlbumEntryIndex(album, albumEntryId));
				if (albumEntryDto == null) {
					return Integer.valueOf(0);
				}
				final BTreeMap<AlbumIndex, AlbumMutationData> currentAlbumMutationMap = currentDataProvider.getAlbumMutationDataMap();
				final AlbumMutationData mutationList = currentAlbumMutationMap.get(album);

				final ArrayList<Mutation> mutations = new ArrayList<Mutation>(mutationList != null ? mutationList.getMutations() : Collections.<Mutation> emptyList());
				boolean modified = false;
				if (values.containsKey(Client.AlbumEntry.META_RATING)) {
					for (final Iterator<Mutation> entryIterator = mutations.iterator(); entryIterator.hasNext();) {
						final Mutation mutationEntry = entryIterator.next();
						if (mutationEntry instanceof RatingMutationEntry && ((EntryMutation) mutationEntry).getAlbumEntryId().equals(albumEntryId)) {
							entryIterator.remove();
							modified = true;
						}
					}
					final Integer newRating = values.getAsInteger(Client.AlbumEntry.META_RATING);
					if (!objectEquals(albumEntryDto.getRating(), newRating)) {
						final RatingMutationEntry newEntry = new RatingMutationEntry();
						newEntry.setAlbumEntryId(albumEntryId);
						newEntry.setBaseVersion(albumEntryDto.getEditableMetadataHash());
						newEntry.setRating(newRating);
						mutations.add(newEntry);
						modified = true;
					}
				}
				if (values.containsKey(Client.AlbumEntry.META_CAPTION)) {
					for (final Iterator<Mutation> entryIterator = mutations.iterator(); entryIterator.hasNext();) {
						final Mutation mutationEntry = entryIterator.next();
						if (mutationEntry instanceof CaptionMutationEntry && ((EntryMutation) mutationEntry).getAlbumEntryId().equals(albumEntryId)) {
							entryIterator.remove();
							modified = true;
						}
					}
					final String newCaption = values.getAsString(Client.AlbumEntry.META_CAPTION);
					if (!objectEquals(albumEntryDto.getCaption(), newCaption)) {
						final CaptionMutationEntry mutationEntry = new CaptionMutationEntry();
						mutationEntry.setAlbumEntryId(albumEntryId);
						mutationEntry.setBaseVersion(albumEntryDto.getEditableMetadataHash());
						mutationEntry.setCaption(newCaption);
						mutations.add(mutationEntry);
						modified = true;
					}
				}

				if (values.containsKey(Client.AlbumEntry.META_KEYWORDS)) {
					for (final Iterator<Mutation> entryIterator = mutations.iterator(); entryIterator.hasNext();) {
						final Mutation mutationEntry = entryIterator.next();
						if (mutationEntry instanceof KeywordMutationEntry && ((EntryMutation) mutationEntry).getAlbumEntryId().equals(albumEntryId)) {
							entryIterator.remove();
							modified = true;
						}
					}
					final Collection<String> remainingKeywords = new HashSet<String>(Client.AlbumEntry.decodeKeywords(values.getAsString(Client.AlbumEntry.META_KEYWORDS)));
					for (final String existingKeyword : albumEntryDto.getKeywords()) {
						final boolean removeThisKeyword = !remainingKeywords.remove(existingKeyword);
						if (removeThisKeyword) {
							final KeywordMutationEntry mutationEntry = new KeywordMutationEntry();
							mutationEntry.setAlbumEntryId(albumEntryId);
							mutationEntry.setBaseVersion(albumEntryDto.getEditableMetadataHash());
							mutationEntry.setKeyword(existingKeyword);
							mutationEntry.setMutation(KeywordMutation.REMOVE);
							mutations.add(mutationEntry);
							modified = true;
						}
					}
					for (final String newKeyword : remainingKeywords) {
						final KeywordMutationEntry mutationEntry = new KeywordMutationEntry();
						mutationEntry.setAlbumEntryId(albumEntryId);
						mutationEntry.setBaseVersion(albumEntryDto.getEditableMetadataHash());
						mutationEntry.setKeyword(newKeyword);
						mutationEntry.setMutation(KeywordMutation.ADD);
						mutations.add(mutationEntry);
						modified = true;
					}
				}
				cursorNotifications.notifySingleAlbumCursorChanged(album);
				if (modified) {
					mutations.trimToSize();
					currentAlbumMutationMap.put(album, new AlbumMutationData(mutations));
				}
				return Integer.valueOf(1);
			}
		}, false).intValue();
	}

	private Map<String, Integer> applyKeywordMutations(final Map<String, Integer> storedCounts, final AlbumMutationData newMutationData) {
		if (newMutationData != null) {
			final Map<String, Integer> albumKeywordCounts = new HashMap<String, Integer>(storedCounts);
			for (final Mutation mutation : newMutationData.getMutations()) {
				if (mutation instanceof KeywordMutationEntry) {
					final KeywordMutationEntry keywordMutation = (KeywordMutationEntry) mutation;
					final String keyword = keywordMutation.getKeyword();
					final Integer existingCounter = albumKeywordCounts.get(keyword);
					final int existingCount = existingCounter == null ? 0 : existingCounter.intValue();
					switch (keywordMutation.getMutation()) {
					case ADD:
						albumKeywordCounts.put(keyword, Integer.valueOf(existingCount + 1));
						break;
					case REMOVE:
						albumKeywordCounts.put(keyword, Integer.valueOf(existingCount - 1));
						break;
					}
				}
			}
			return albumKeywordCounts;
		} else {
			return storedCounts;
		}
	}

	private <V> V callInTransaction(final Callable<V> callable, final boolean readOnly) {
		return cursorNotifications.doWithNotify(new Callable<V>() {
			@Override
			public V call() throws Exception {
				int retryCount = 0;
				while (true) {
					final DataProvider oldDataProvider = getCurrentDataProvider();
					@Cleanup
					final DB newTransaction = mapDB.makeTx();
					currentMapDbTransaction.set(new DataProvider(newTransaction));
					try {
						final V retValue = callable.call();
						if (!readOnly) {
							newTransaction.commit();
						}
						return retValue;
					} catch (final TxRollbackException ex) {
						if (retryCount++ < 5) {
							continue;
						}
						throw ex;
					} finally {
						currentMapDbTransaction.set(oldDataProvider);
					}
				}
			}
		});
	}

	private <V> V callWithLongTransaction(final TransactionCallable<V> callable) {
		final DataProvider dataProvider = new DataProvider(mapDB.makeTx());
		boolean exceptionCatched = true;
		try {
			final V ret = callable.call(dataProvider);
			exceptionCatched = false;
			return ret;
		} finally {
			if (exceptionCatched) {
				dataProvider.close();
			}
		}
	}

	private void closeIfCloseable(final Object object) {
		if (object instanceof Closeable) {
			try {
				((Closeable) object).close();
			} catch (final IOException e) {
				throw new RuntimeException("Cannot close " + object, e);
			}
		}

	}

	private Collection<AlbumIndex> collectVisibleAlbums() {
		final Collection<AlbumIndex> ret = new LinkedHashSet<AlbumIndex>();
		for (final Entry<String, ConcurrentMap<String, String>> archiveEntry : visibleAlbums.entrySet()) {
			final String archiveName = archiveEntry.getKey();
			for (final String albumId : archiveEntry.getValue().values()) {
				ret.add(AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build());
			}
		}
		return ret;
	}

	private boolean dateEquals(final Date date1, final Date date2) {
		if (date1 == null) {
			return date2 == null;
		}
		if (date2 == null) {
			return false;
		}
		return Math.abs(date1.getTime() - date2.getTime()) < 1000;
	}

	private Collection<String> findStoragesOfAlbum(final AlbumIndex key) {
		final DataProvider currentDataProvider = getCurrentDataProvider();
		final ArchiveMeta storageList = currentDataProvider.getArchiveMetaMap().get(key.getArchiveName());
		final HashSet<String> ret = new HashSet<String>();
		if (storageList != null) {
			for (final StorageEntry entry : storageList.getClients()) {
				if (entry.getAlbumList().contains(key.getAlbumId())) {
					ret.add(entry.getStorageId());
				}
			}
		}
		final AlbumMutationData mutationData = currentDataProvider.getAlbumMutationDataMap().get(key);
		if (mutationData != null) {
			for (final Mutation mutation : mutationData.getMutations()) {
				if (mutation instanceof StorageMutation) {
					final StorageMutation storageMutationEntry = (StorageMutation) mutation;
					switch (storageMutationEntry.getMutation()) {
					case ADD:
						ret.add(storageMutationEntry.getStorage());
						break;
					case REMOVE:
						ret.remove(storageMutationEntry.getStorage());
						break;
					}
				}
			}
		}
		return ret;
	}

	private String getBasename(final String fileName) {
		final int lastPt = fileName.lastIndexOf('.');
		if (lastPt < 0) {
			return fileName;
		}
		return fileName.substring(0, lastPt);
	}

	private ServerConnection getConnectionForServer(final String serverId) {
		final Map<String, ArchiveConnection> archives = connectionMap.get();

		ServerConnection serverConnection = null;
		for (final Entry<String, ArchiveConnection> archiveEntry : archives.entrySet()) {
			for (final ServerConnection server : archiveEntry.getValue().listServers().values()) {
				if (server.getInstanceId().equals(serverId)) {
					serverConnection = server;
				}
			}
		}
		return serverConnection;
	}

	private DataProvider getCurrentDataProvider() {
		return currentMapDbTransaction.get();
	}

	private ThumbnailEntry ifExsists(final File file, final boolean confirmed) {
		if (!file.exists()) {
			return null;
		}
		final ThumbnailEntry ret = new ThumbnailEntry();
		ret.referencedFile = file;
		ret.confirmedByServer = confirmed;
		return ret;
	}

	private void initServiceDiscovery() {
		dnsListener = JMDnsListener.builder().context(getApplicationContext()).resultListener(this).executorService(executorService).build();
		// dnsListener = NsdListener.builder().context(getApplicationContext()).resultListener(this).build();
	}

	/**
	 *
	 * initializes thumbnail-cache
	 *
	 * @param size
	 *          Cache-Size in kB
	 */
	private void initThumbnailCache(final int size) {
		thumbnailCache = new LruCache<AlbumEntryIndex, ThumbnailEntry>(size) {

			@Override
			protected ThumbnailEntry create(final AlbumEntryIndex key) {
				return loadThumbnail(key);
			}

			@Override
			protected void entryRemoved(final boolean evicted, final AlbumEntryIndex key, final ThumbnailEntry oldValue, final ThumbnailEntry newValue) {
				final File referencedFile = oldValue.referencedFile;
				if (referencedFile == null) {
					// no file referenced
					return;
				}
				if (!thumbnailsTempDir.equals(referencedFile.getParentFile())) {
					return;
				}
				final boolean deleted = referencedFile.delete();
				if (!deleted) {
					throw new RuntimeException("Cannot delete cache-file " + oldValue);
				}
			}

			@Override
			protected int sizeOf(final AlbumEntryIndex key, final ThumbnailEntry value) {
				final File referencedFile = value.referencedFile;
				if (referencedFile == null) {
					return 0;
				}
				if (!thumbnailsTempDir.equals(referencedFile.getParentFile())) {
					// count only temporary entries
					return 0;
				}
				return (int) referencedFile.length() / 1024;
			}
		};
		executorService.schedule(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				refreshThumbnailsFromFiles();
				return null;
			}
		}, 10, TimeUnit.SECONDS);
	}

	private String lastPart(final String[] split) {
		if (split == null || split.length == 0) {
			return null;
		}
		return split[split.length - 1];
	}

	private ThumbnailEntry loadThumbnail(final AlbumEntryIndex entry) {
		return callInTransaction(new Callable<ThumbnailEntry>() {

			@Override
			public ThumbnailEntry call() throws Exception {
				final AlbumIndex albumIndex = entry.getAlbumIndex();
				final String archiveName = albumIndex.getArchiveName();
				final String albumId = albumIndex.getAlbumId();
				final String albumEntryId = entry.getAlbumEntryId();
				// final long startTime = System.currentTimeMillis();
				try {
					final DataProvider currentDataProvider = getCurrentDataProvider();
					final AlbumMeta albumMeta = currentDataProvider.getAlbumMetadataMap().get(albumIndex);
					final AlbumState albumState = currentDataProvider.getAlbumStateMap().get(albumIndex);

					if (albumMeta == null) {
						return null;
					}
					final boolean permanentDownload = albumState != null && albumState.isShouldSync();
					final AlbumEntryDto albumEntryDto = currentDataProvider.getAlbumEntryMap().get(entry);
					if (albumEntryDto == null) {
						return null;
					}
					final String externalSuffix = albumEntryDto.getEntryType() == AlbumEntryType.IMAGE ? ".jpg" : ".mp4";
					final File temporaryTargetFile = new File(thumbnailsTempDir, archiveName + "/" + albumId + "/" + albumEntryId + THUMBNAIL_SUFFIX);
					final String fileName = getBasename(albumEntryDto.getFileName());
					final File permanentTargetFile = new File(thumbnailsSyncDir, archiveName + "/" + albumMeta.getName() + "/" + fileName + externalSuffix);
					final File targetFile = permanentDownload ? permanentTargetFile : temporaryTargetFile;
					final File otherTargetFile = permanentDownload ? temporaryTargetFile : permanentTargetFile;
					// check if the file in the current cache is valid
					if (targetFile.exists() && targetFile.lastModified() >= albumEntryDto.getLastModified().getTime()) {
						if (otherTargetFile.exists()) {
							otherTargetFile.delete();
						}
						return ifExsists(targetFile, true);
					}
					// check if there is a valid file in the other cache
					if (otherTargetFile.exists()) {
						if (otherTargetFile.lastModified() >= albumEntryDto.getLastModified().getTime()) {
							final long oldLastModified = otherTargetFile.lastModified();
							otherTargetFile.renameTo(targetFile);
							targetFile.setLastModified(oldLastModified);
							if (targetFile.exists()) {
								return ifExsists(targetFile, true);
							}

						}
						// remove the invalid file of the other cache
						otherTargetFile.delete();
					}
					final File parentDir = targetFile.getParentFile();
					if (!parentDir.exists()) {
						parentDir.mkdirs();
					}
					final Map<String, ArchiveConnection> archive = connectionMap.get();
					if (archive == null) {
						return ifExsists(targetFile, false);
					}
					final ArchiveConnection archiveConnection = archive.get(archiveName);
					if (archiveConnection == null) {
						return ifExsists(targetFile, false);
					}
					final AlbumConnection albumConnection = archiveConnection.getAlbums().get(albumMeta.getName());
					if (albumConnection == null) {
						return ifExsists(targetFile, false);
					}

					final File tempFile = new File(parentDir, tempFileId.incrementAndGet() + ".thumbnail-temp");
					if (tempFile.exists()) {
						tempFile.delete();
					}
					try {
						final boolean readOk = albumConnection.readThumbnail(albumEntryId, tempFile, targetFile);
						return ifExsists(targetFile, readOk);
					} finally {
						if (tempFile.exists()) {
							tempFile.delete();
						}
					}
				} finally {
					// Log.i("Performance", "Loaded Thumbnail " + archiveName + ":" + albumId + ":" + albumEntryId + " in " + (System.currentTimeMillis() -
					// startTime) + " ms");
				}
			}
		}, true);
	}

	private void loadThumbnailsOfAlbum(final AlbumIndex albumIndex) {
		callInTransaction(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				for (int i = 0; i < 2; i++) {
					boolean allOk = true;
					for (final AlbumEntryIndex thumbnailId : getCurrentDataProvider().listEntriesByAlbum(albumIndex)) {
						final ThumbnailEntry thumbnailEntry = thumbnailCache.get(thumbnailId);
						if (thumbnailEntry != null && !thumbnailEntry.confirmedByServer && i == 0) {
							thumbnailCache.remove(thumbnailId);
						}
						allOk &= thumbnailEntry != null && thumbnailEntry.confirmedByServer;
					}
					if (allOk) {
						final BTreeMap<AlbumIndex, AlbumState> albumStateMap = getCurrentDataProvider().getAlbumStateMap();
						albumStateMap.putIfAbsent(albumIndex, AlbumState.builder().build());
						final AlbumState oldAlbumState = albumStateMap.get(albumIndex);
						albumStateMap.put(albumIndex, oldAlbumState.withSynced(true));
						cursorNotifications.notifySingleAlbumCursorChanged(albumIndex);
						return null;
					}
				}
				return null;
			}
		}, true);
	}

	private SortOrder makeAlbumDefaultSortOrder() {
		final SortOrder defaultSortOrder = new SortOrder();
		defaultSortOrder.addOrder(Client.Album.ALBUM_CAPTURE_DATE, Order.DESC);
		defaultSortOrder.addOrder(Client.Album.NAME, Order.ASC);
		return defaultSortOrder;
	}

	private Cursor makeCursorForAlbumEntries(final Iterable<AlbumEntryIndex> indexedAccess,
	                                         final String[] projection,
	                                         final Collection<AlbumIndex> albumsToNotify,
	                                         final Criterium criterium,
	                                         final SortOrder order,
	                                         final DataProvider dataProvider) {
		Log.i(SERVICE_TAG, "Start prepare album entries");

		final BTreeMap<AlbumEntryIndex, AlbumEntryDto> albumEntryMap = dataProvider.getAlbumEntryMap();
		final Map<AlbumEntryIndex, Date> albumEntryCreationDate = dataProvider.getAlbumEntryCreationDate();
		final BTreeMap<AlbumIndex, AlbumMutationData> albumMutationDataMap = dataProvider.getAlbumMutationDataMap();
		final Map<String, FieldReader<AlbumEntryIndex>> indexFieldReaders = MapperUtil.delegateFieldReaders(MapperUtil.makeAnnotatedFieldReaders(AlbumEntryDto.class),
		                                                                                                    LazyLoader.lookupMap(albumEntryMap));

		indexFieldReaders.put(Client.AlbumEntry.CAPTURE_DATE, new NumericFieldReader<AlbumEntryIndex>(Cursor.FIELD_TYPE_INTEGER) {

			@Override
			public Number getNumber(final AlbumEntryIndex value) {
				final Date dateValue = readDateValue(value);
				if (dateValue == null) {
					return null;
				}
				return Long.valueOf(dateValue.getTime());
			}

			@Override
			public String getString(final AlbumEntryIndex value) {
				final Date dateValue = readDateValue(value);
				if (dateValue == null) {
					return null;
				}
				return dateValue.toString();
			}

			private Date readDateValue(final AlbumEntryIndex value) {
				return albumEntryCreationDate.get(value);
			}
		});

		indexFieldReaders.put(Client.AlbumEntry.META_KEYWORDS, new StringFieldReader<AlbumEntryIndex>() {

			@Override
			public String getString(final AlbumEntryIndex value) {
				final AlbumEntryDto albumEntryDto = albumEntryMap.get(value);
				if (albumEntryDto == null) {
					return Client.AlbumEntry.encodeKeywords(Collections.<String> emptyList());
				}
				final Collection<String> keywords = new TreeSet<String>(albumEntryDto.getKeywords());
				final AlbumMutationData mutationData = albumMutationDataMap.get(value.getAlbumIndex());
				if (mutationData != null && mutationData.getMutations() != null) {
					for (final Mutation mutation : mutationData.getMutations()) {
						if (mutation instanceof KeywordMutationEntry) {
							final KeywordMutationEntry keywordEntry = (KeywordMutationEntry) mutation;
							if (!keywordEntry.getAlbumEntryId().equals(value.getAlbumEntryId())) {
								continue;
							}
							switch (keywordEntry.getMutation()) {
							case ADD:
								keywords.add(keywordEntry.getKeyword());
								break;
							case REMOVE:
								keywords.remove(keywordEntry.getKeyword());
								break;
							}
						}
					}
				}
				return Client.AlbumEntry.encodeKeywords(keywords);
			}
		});
		indexFieldReaders.put(Client.AlbumEntry.THUMBNAIL, new StringFieldReader<AlbumEntryIndex>() {

			@Override
			public String getString(final AlbumEntryIndex value) {
				return Client.makeThumbnailUri(value.getAlbumIndex().getArchiveName(), value.getAlbumIndex().getAlbumId(), value.getAlbumEntryId()).toString();
			}
		});
		indexFieldReaders.put(Client.AlbumEntry.THUMBNAIL_ALIAS, new StringFieldReader<AlbumEntryIndex>() {

			@Override
			public String getString(final AlbumEntryIndex value) {
				final AlbumEntryDto entryDto = albumEntryMap.get(value);
				if (entryDto == null) {
					return null;
				}
				return Client.makeThumbnailString(value.getAlbumIndex().getArchiveName(), value.getAlbumIndex().getAlbumId(), value.getAlbumEntryId()).toString() + "/"
				       + entryDto.getFileName();
			}
		});
		indexFieldReaders.put(Client.AlbumEntry.NUMERIC_ID, new NumericFieldReader<AlbumEntryIndex>(Cursor.FIELD_TYPE_INTEGER) {

			@Override
			public Number getNumber(final AlbumEntryIndex value) {
				return idCache.get(value.getAlbumIndex().getArchiveName() + "_" + value.getAlbumIndex().getAlbumId() + "_" + value.getAlbumEntryId());
			}
		});
		indexFieldReaders.put(Client.AlbumEntry.ENTRY_URI, new StringFieldReader<AlbumEntryIndex>() {

			@Override
			public String getString(final AlbumEntryIndex value) {
				return Client.makeAlbumEntryString(value.getAlbumIndex().getArchiveName(), value.getAlbumIndex().getAlbumId(), value.getAlbumEntryId());
			}
		});
		Log.i(SERVICE_TAG, "End Prepare, Start iteration");
		try {
			final NotifyableCursor cursor = MapperUtil.loadConnectionIntoWindowedCursor(indexedAccess, projection, indexFieldReaders, criterium, order, new Runnable() {

				@Override
				public void run() {
					dataProvider.close();
				}
			});
			Log.i(SERVICE_TAG, "End iteration");
			for (final AlbumIndex index : albumsToNotify) {
				cursorNotifications.addSingleAlbumCursor(index, cursor);
			}
			return cursor;
		} finally {
			Log.i(SERVICE_TAG, "Returning");
		}
	}

	private Cursor makeCursorForAlbums(final String[] projection, final Criterium criterium, final SortOrder order) throws SQLException {
		return callInTransaction(new Callable<Cursor>() {

			@Override
			public Cursor call() throws Exception {
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final BTreeMap<AlbumIndex, AlbumMeta> loadedAlbums = currentDataProvider.getAlbumMetadataMap();
				final ArrayList<AlbumMeta> albums = new ArrayList<AlbumMeta>(loadedAlbums.values());
				final Map<AlbumIndex, AlbumState> albumStateLoader = currentDataProvider.getAlbumStateMap();
				final BTreeMap<AlbumIndex, AlbumMutationData> albumMutationDataMap = currentDataProvider.getAlbumMutationDataMap();
				final LazyLoader.Callable<Collection<AlbumIndex>> visibleAlbumsLoader = LazyLoader.loadLazy(new LazyLoader.Callable<Collection<AlbumIndex>>() {
					@Override
					public Collection<AlbumIndex> call() {
						return collectVisibleAlbums();
					}
				});
				final Map<String, FieldReader<AlbumMeta>> fieldReaders = MapperUtil.makeAnnotatedFieldReaders(AlbumMeta.class);
				fieldReaders.put(Client.Album.AUTOADD_DATE, new StringFieldReader<AlbumMeta>() {

					@Override
					public String getString(final AlbumMeta value) {
						return Client.Album.encodeAutoaddDates(value.getAutoAddDate());
					}
				});
				fieldReaders.put(Client.Album.VISIBLE_SERVER_COUNT, new NumericFieldReader<AlbumMeta>(Cursor.FIELD_TYPE_INTEGER) {

					@Override
					public Number getNumber(final AlbumMeta value) {
						final AlbumIndex index = AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build();
						return Integer.valueOf(visibleAlbumsLoader.call().contains(index) ? 1 : 0);
					}
				});
				fieldReaders.put(Client.Album.THUMBNAIL, new StringFieldReader<AlbumMeta>() {
					@Override
					public String getString(final AlbumMeta value) {
						String thumbnailId = value.getThumbnailId();

						final AlbumMutationData mutationData = albumMutationDataMap.get(AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build());
						if (mutationData != null) {
							for (final Mutation mutation : mutationData.getMutations()) {
								if (mutation instanceof TitleImageMutation) {
									thumbnailId = ((TitleImageMutation) mutation).getTitleImage();
								}
							}
						}
						if (thumbnailId == null) {
							return null;
						}
						return Client.makeThumbnailString(value.getArchiveName(), value.getAlbumId(), thumbnailId);
					}
				});
				fieldReaders.put(Client.Album.TITLE, new StringFieldReader<AlbumMeta>() {

					@Override
					public String getString(final AlbumMeta value) {
						String albumTitle = value.getAlbumTitle();
						final AlbumMutationData mutationData = albumMutationDataMap.get(AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build());
						if (mutationData != null) {
							for (final Mutation mutation : mutationData.getMutations()) {
								if (mutation instanceof TitleMutation) {
									albumTitle = ((TitleMutation) mutation).getTitle();
								}
							}
						}
						return albumTitle;
					}
				});
				fieldReaders.put(Client.Album.ENTRY_URI, new StringFieldReader<AlbumMeta>() {
					@Override
					public String getString(final AlbumMeta value) {
						return Client.makeAlbumUri(value.getArchiveName(), value.getAlbumId()).toString();
					}
				});
				fieldReaders.put(Client.Album.ALBUM_ENTRIES_URI, new StringFieldReader<AlbumMeta>() {

					@Override
					public String getString(final AlbumMeta value) {
						return Client.makeAlbumEntriesUri(value.getArchiveName(), value.getAlbumId()).toString();
					}
				});
				fieldReaders.put(Client.Album.NUMERIC_ID, new NumericFieldReader<AlbumMeta>(Cursor.FIELD_TYPE_INTEGER) {

					@Override
					public Number getNumber(final AlbumMeta value) {
						return idCache.get(value.getAlbumId());
					}
				});
				fieldReaders.put(Client.Album.SHOULD_SYNC, new BooleanFieldReader<AlbumMeta>() {

					@Override
					public Boolean getBooleanValue(final AlbumMeta value) {
						final AlbumState albumState = albumStateLoader.get(AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build());
						return Boolean.valueOf(albumState.isShouldSync());
					}
				});
				fieldReaders.put(Client.Album.SYNCED, new BooleanFieldReader<AlbumMeta>() {

					@Override
					public Boolean getBooleanValue(final AlbumMeta value) {
						final AlbumState albumState = albumStateLoader.get(AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build());
						return Boolean.valueOf(albumState.isSynced());
					}
				});
				final Lookup<AlbumIndex, Collection<String>> lookupStorages = new Lookup<AlbumIndex, Collection<String>>() {

					@Override
					public Collection<String> get(final AlbumIndex key) {
						return findStoragesOfAlbum(key);
					}
				};
				fieldReaders.put(Client.Album.STORAGES, new StringFieldReader<AlbumMeta>() {

					@Override
					public String getString(final AlbumMeta value) {
						return Client.Album.encodeStorages(lookupStorages.get(AlbumIndex.builder().archiveName(value.getArchiveName()).albumId(value.getAlbumId()).build()));
					}
				});

				return cursorNotifications.addAllAlbumCursor(MapperUtil.loadCollectionIntoCursor(albums,
				                                                                                 projection,
				                                                                                 fieldReaders,
				                                                                                 criterium,
				                                                                                 order == null ? makeAlbumDefaultSortOrder() : order));
			}
		},
		                         true);
	}

	private SortOrder makeDefaultAlbumEntiesOrder() {
		final SortOrder defaultOrder = new SortOrder();
		defaultOrder.addOrder(Client.AlbumEntry.CAPTURE_DATE, Order.ASC, false);
		defaultOrder.addOrder(Client.AlbumEntry.NAME, Order.ASC, false);
		return defaultOrder;
	}

	private long makeLongId(final String stringId) {
		return idCache.get(stringId).longValue();
	}

	private Builder makeNotificationBuilder() {
		final PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), ServerListActivity.class), 0);
		final Builder builder = new Notification.Builder(this).setContentTitle("Syncing").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(intent);
		return builder;
	}

	private URL makeUrl(final InetSocketAddress inetSocketAddress) {
		try {
			final InetAddress targetAddress = inetSocketAddress.getAddress();
			if (targetAddress instanceof Inet6Address) {
				final int scopedInterface = ((Inet6Address) targetAddress).getScopeId();
				final String hostName = targetAddress.getHostName();
				if (scopedInterface != 0 && targetAddress.isLinkLocalAddress()) {
					final String hostAddress = "[" + hostName + "%" + scopedInterface + "]";
					return new URL("http", hostAddress, inetSocketAddress.getPort(), "rest");
				}
				return new URL("http", "[" + hostName + "]", inetSocketAddress.getPort(), "rest");
			}
			return new URL("http", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), "rest");
		} catch (final MalformedURLException e) {
			throw new RuntimeException("Cannot create URL for Socket " + inetSocketAddress, e);
		}
	}

	private void notifyAlbumChanged(final AlbumIndex id) {
		cursorNotifications.notifySingleAlbumCursorChanged(id);
	}

	private void notifyAlbumListChanged() {
		cursorNotifications.notifyAllAlbumCursorsChanged();
	}

	private <O> boolean objectEquals(final O v1, final O v2) {
		if (v1 == v2) {
			return true;
		}
		if (v1 == null || v2 == null) {
			return false;
		}
		return v1.equals(v2);
	}

	private PingResponse pingService(final URL url) {
		final RestTemplate restTemplate = new RestTemplate(true);
		try {
			try {
				final ResponseEntity<PingResponse> entity = restTemplate.getForEntity(url + "/ping.json", PingResponse.class);
				final boolean pingOk = entity.getStatusCode().series() == Series.SUCCESSFUL;
				if (pingOk) {
					return entity.getBody();
				} else {
					Log.i(SERVICE_TAG, "Error connecting Service at " + url + ", " + entity.getStatusCode() + " " + entity.getStatusCode().getReasonPhrase());
					return null;
				}
			} catch (final ResourceAccessException ex) {
				final Throwable cause = ex.getCause();
				if (cause != null && cause instanceof ConnectException) {
					// try next
					Log.d(SERVICE_TAG, "Connect to " + url + "/ failed, try more");
					return null;
				} else if (cause != null && cause instanceof UnknownHostException) {
					Log.d(SERVICE_TAG, "Connect to " + url + "/ failed cause of spring-bug with ipv6, try more");
					return null;
				} else {
					throw ex;
				}
			} catch (final RestClientException ex) {
				Log.d(SERVICE_TAG, "Connect to " + url + "/ failed, try more");
				return null;
			} catch (final IllegalStateException ex) {
				final Throwable cause = ex.getCause();
				if (cause != null && cause instanceof URISyntaxException) {
					Log.d(SERVICE_TAG, "Connect to " + url + "/ failed cause of android-bug with ipv6 and link-local uris, try more");
					return null;
				} else {
					throw ex;
				}
			}
		} catch (final Exception ex) {
			throw new RuntimeException("Cannot connect to " + url, ex);
		}
	}

	private void pollServers() {
		if (pollServerSemaphore.tryAcquire()) {
			try {
				final ServerDiscoveryListener listener = dnsListener;
				if (listener != null) {
					listener.pollForServices(true);
				}
			} catch (final Throwable t) {
				Log.w(SERVICE_TAG, "Exception while polling", t);
			} finally {
				pollServerSemaphore.release();
			}
		} else {
			// refresh server-state anyway
			updateServerCursors();
		}
	}

	/**
	 * Collect all pending Mutations and send it to the server
	 *
	 * @param albumConnection
	 * @param albumId
	 * @param albumId
	 */
	private void pushPendingMetadataUpdate(final AlbumConnection albumConnection, final AlbumIndex album) {
		final AlbumMutationData mutations = getCurrentDataProvider().getAlbumMutationDataMap().get(album);
		if (mutations == null || mutations.getMutations().isEmpty()) {
			// no pending mutation found
			return;
		}
		albumConnection.updateMetadata(mutations.getMutations());
	}

	private <K, V> V putIfNotExists(final ConcurrentMap<K, V> map, final K key, final V emptyValue) {
		final V existingValue = map.putIfAbsent(key, emptyValue);
		if (existingValue != null) {
			return existingValue;
		}
		return emptyValue;
	}

	private void refreshAlbumDetail(final AlbumConnection albumConnection, final AlbumIndex entry) {
		// read data from Server
		final AlbumDto albumDto = albumConnection.getAlbumDetail();
		callInTransaction(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				// clear pending mutation-data if it exists
				final DataProvider currentDataProvider = getCurrentDataProvider();
				final BTreeMap<AlbumIndex, AlbumMutationData> albumMutationDataMap = currentDataProvider.getAlbumMutationDataMap();
				final BTreeMap<AlbumEntryIndex, AlbumEntryDto> albumEntryMap = currentDataProvider.getAlbumEntryMap();
				final AlbumMutationData mutationList = albumMutationDataMap.get(entry);
				final Collection<Mutation> mutations = mutationList != null && mutationList.getMutations() != null ? new ArrayList<Mutation>(mutationList.getMutations())
				    : Collections.<Mutation> emptyList();
				final Collection<AlbumEntryIndex> entriesToRemove = new HashSet<AlbumEntryIndex>();
				for (final AlbumEntryIndex entryIndex : currentDataProvider.listEntriesByAlbum(entry)) {
					entriesToRemove.add(entryIndex);
				}
				for (final AlbumEntryDto newEntry : albumDto.getEntries().values()) {
					final String entryId = newEntry.getCommId();
					final AlbumEntryIndex entryIndex = new AlbumEntryIndex(entry, entryId);
					albumEntryMap.put(entryIndex, newEntry);
					entriesToRemove.remove(entryIndex);
				}
				for (final AlbumEntryIndex albumEntryIndex : entriesToRemove) {
					albumEntryMap.remove(albumEntryIndex);
				}
				for (final Iterator<Mutation> entryIterator = mutations.iterator(); entryIterator.hasNext();) {
					final Mutation mutation = entryIterator.next();
					if (mutation instanceof AlbumMutation && ObjectUtils.objectEquals(((AlbumMutation) mutation).getAlbumLastModified(), albumDto.getLastModified())) {
						entryIterator.remove();
					}
				}

				final AtomicLong dateSum = new AtomicLong(0);
				final AtomicLong thumbnailSizeSum = new AtomicLong(0);
				final AtomicLong originalSizeSum = new AtomicLong(0);
				final AtomicInteger dateCount = new AtomicInteger(0);
				final Map<String, Integer> keywordCounts = new HashMap<String, Integer>();

				for (final Entry<String, AlbumEntryDto> albumImageEntry : albumDto.getEntries().entrySet()) {

					final AlbumEntryDto entryDto = albumImageEntry.getValue();
					final String imageId = albumImageEntry.getKey();
					final String editableMetadataHash = entryDto.getEditableMetadataHash();
					for (final Iterator<Mutation> entryIterator = mutations.iterator(); entryIterator.hasNext();) {
						final Mutation mutationEntry = entryIterator.next();
						if (mutationEntry instanceof EntryMutation && ((EntryMutation) mutationEntry).getAlbumEntryId().equals(imageId)
						    && !((EntryMutation) mutationEntry).getBaseVersion().equals(editableMetadataHash)) {
							entryIterator.remove();
						}
					}
					if (entryDto.getCaptureDate() != null) {
						dateCount.incrementAndGet();
						dateSum.addAndGet(entryDto.getCaptureDate().getTime());
					}
					final Long thumbnailSize = entryDto.getThumbnailSize();
					if (thumbnailSize != null) {
						thumbnailSizeSum.addAndGet(thumbnailSize.longValue());
					}
					originalSizeSum.addAndGet(entryDto.getOriginalFileSize());
					for (final String keywordEntry : entryDto.getKeywords()) {
						final Integer oldCount = keywordCounts.get(keywordEntry);
						if (oldCount != null) {
							keywordCounts.put(keywordEntry, Integer.valueOf(oldCount.intValue() + 1));
						} else {
							keywordCounts.put(keywordEntry, Integer.valueOf(1));
						}
					}
				}
				final AlbumMetaBuilder builder = AlbumMeta.builder();
				builder.albumTitle(albumDto.getAlbumTitle());
				builder.keywordCounts(keywordCounts);
				builder.lastModified(albumDto.getLastModified());
				builder.autoAddDate(albumDto.getAutoAddDate());
				builder.entryCount(albumDto.getEntries().size());
				builder.thumbnailId(albumDto.getAlbumTitleEntry());
				builder.thumbnailSize(thumbnailSizeSum.get());
				builder.originalsSize(originalSizeSum.get());
				builder.albumId(entry.getAlbumId());
				builder.archiveName(entry.getArchiveName());
				if (dateCount.get() > 0) {
					builder.albumDate(new Date(dateSum.longValue() / dateCount.longValue()));
				}
				currentDataProvider.getAlbumMetadataMap().put(entry, builder.build());

				if (mutations.isEmpty()) {
					albumMutationDataMap.remove(entry);
				} else if (mutations.size() != mutationList.getMutations().size()) {
					albumMutationDataMap.put(entry, new AlbumMutationData(mutations));
				}
				notifyAlbumChanged(entry);
				return null;
			}
		}, false);
	}

	private void refreshThumbnailsFromFiles() {
		for (final File archiveDir : thumbnailsTempDir.listFiles(new FileFilter() {

			@Override
			public boolean accept(final File pathname) {
				return pathname.isDirectory();
			}
		})) {
			final String archiveName = archiveDir.getName();
			for (final File albumDir : archiveDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File pathname) {
					return pathname.isDirectory();
				}
			})) {
				final String albumId = albumDir.getName();
				final AlbumIndex album = AlbumIndex.builder().archiveName(archiveName).albumId(albumId).build();
				for (final File thumbnailFile : albumDir.listFiles(new FileFilter() {

					@Override
					public boolean accept(final File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(THUMBNAIL_SUFFIX);
					}
				})) {
					final String filename = thumbnailFile.getName();
					final String albumEntryId = filename.substring(0, filename.length() - THUMBNAIL_SUFFIX.length());
					final ThumbnailEntry loadedFile = thumbnailCache.get(new AlbumEntryIndex(album, albumEntryId));
					if (loadedFile == null) {
						thumbnailFile.delete();
					}
				}
			}
		}
	}

	private void registerScreenOnOff() {
		final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(new PowerStateReceiver(), intentFilter);
		executorService.schedule(new Runnable() {

			@Override
			public void run() {
				PowerStateReceiver.notifyPowerState(getApplicationContext());
			}
		}, 5, TimeUnit.SECONDS);
	}

	private synchronized void startFastPolling() {
		if (fastUpdatePollingFuture == null || fastUpdatePollingFuture.isCancelled()) {
			fastUpdatePollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {
					pollServers();
				}
			}, 20, 30, TimeUnit.SECONDS);
		}

	}

	private synchronized void startRunning() {
		if (running.get()) {
			return;
		}
		running.set(true);
		Log.i(SERVICE_TAG, "Synchronisation started");
		dnsListener.startListening();
		final Notification notification = makeNotificationBuilder().getNotification();
		notificationManager.notify(NOTIFICATION, notification);
		startSlowPolling();
	}

	private synchronized void startSlowPolling() {
		if (slowUpdatePollingFuture == null || slowUpdatePollingFuture.isCancelled()) {
			slowUpdatePollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					pollServers();
				}
			}, 10, 20, TimeUnit.MINUTES);
		}

	}

	private synchronized void stopFastPolling() {
		if (fastUpdatePollingFuture != null) {
			fastUpdatePollingFuture.cancel(false);
		}
	}

	private synchronized void stopRunning() {
		dnsListener.stopListening();
		notificationManager.cancel(NOTIFICATION);
		stopSlowPolling();
		stopFastPolling();
		running.set(false);
	}

	private synchronized void stopSlowPolling() {
		if (slowUpdatePollingFuture != null) {
			slowUpdatePollingFuture.cancel(false);
		}
	}

	private void updateAlbumDetail(final String archiveName,
	                               final String albumName,
	                               final AlbumConnection albumConnection,
	                               final int totalAlbumCount,
	                               final AtomicInteger albumCounter) {
		final ConcurrentMap<String, String> visibleAlbumsOfArchive = putIfNotExists(visibleAlbums, archiveName, new ConcurrentHashMap<String, String>());

		final Notification.Builder builder = makeNotificationBuilder();
		builder.setContentTitle("DB Update");
		builder.setContentText("Downloading " + lastPart(albumName.split("/")) + " from " + archiveName);
		builder.setProgress(totalAlbumCount, albumCounter.incrementAndGet(), false);
		notificationManager.notify(NOTIFICATION, builder.getNotification());

		final AtomicBoolean shouldUpdateMeta = new AtomicBoolean(false);
		final AtomicBoolean shouldLoadThumbnails = new AtomicBoolean(false);
		final String commId = albumConnection.getCommId();
		final AlbumIndex index = AlbumIndex.builder().archiveName(archiveName).albumId(commId).build();
		callInTransaction(new Callable<Void>() {
			@Override
			public Void call() throws Exception {

				final DataProvider currentDataProvider = getCurrentDataProvider();
				final BTreeMap<AlbumIndex, AlbumMeta> currentAlbumMetaMap = currentDataProvider.getAlbumMetadataMap();
				final boolean albumModified;
				if (!currentAlbumMetaMap.containsKey(index)) {
					final AlbumMetaBuilder builder = AlbumMeta.builder();
					builder.albumId(index.getAlbumId());
					builder.archiveName(index.getArchiveName());
					builder.name(albumName);
					builder.autoAddDate(Collections.<Date> emptyList());
					builder.keywordCounts(Collections.<String, Integer> emptyMap());
					currentAlbumMetaMap.put(index, builder.build());
					albumModified = true;
				} else {
					final AlbumMeta existingAlbumMeta = currentAlbumMetaMap.get(index);
					albumModified = !dateEquals(existingAlbumMeta.getLastModified(), albumConnection.lastModified());
					if (!objectEquals(albumName, existingAlbumMeta.getName())) {
						final AlbumMeta modifiedAlbumMeta = existingAlbumMeta.withName(albumName);
						currentAlbumMetaMap.put(index, modifiedAlbumMeta);
					}
				}
				final BTreeMap<AlbumIndex, AlbumState> albumStateMap = currentDataProvider.getAlbumStateMap();
				albumStateMap.putIfAbsent(index, AlbumState.builder().build());
				final AlbumState albumState = albumStateMap.get(index);
				shouldUpdateMeta.set(albumModified);
				if (albumModified) {
					albumStateMap.put(index, albumState.withSynced(false));
					notifyAlbumListChanged();
				}
				shouldLoadThumbnails.set(albumState.isShouldSync() && !albumState.isSynced());

				final boolean visibleAlbumsModified = visibleAlbumsOfArchive.put(albumName, index.getAlbumId()) == null;
				if (visibleAlbumsModified) {
					notifyAlbumListChanged();
				}
				return null;
			}

		}, false);
		if (shouldUpdateMeta.get()) {
			refreshAlbumDetail(albumConnection, index);
		}
		callInTransaction(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				pushPendingMetadataUpdate(albumConnection, index);
				return null;
			}
		}, true);
		if (shouldLoadThumbnails.get()) {
			loadThumbnailsOfAlbum(index);
		}
	}

	private void updateAlbumsOnDB() {
		final boolean hasLock = updateLockSempahore.tryAcquire();
		if (hasLock) {
			try {
				final Notification.Builder builder = makeNotificationBuilder();
				builder.setContentTitle("DB Update");
				notificationManager.notify(NOTIFICATION, builder.getNotification());

				// remove invisible archives
				boolean visibleAlbumsModified = visibleAlbums.keySet().retainAll(connectionMap.get().keySet());

				final Collection<Callable<Void>> updateDetailRunnables = new ArrayList<Callable<Void>>();

				for (final Entry<String, ArchiveConnection> archive : connectionMap.get().entrySet()) {
					if (!running.get()) {
						break;
					}
					final String archiveName = archive.getKey();
					final ArchiveConnection archiveConnection = archive.getValue();
					final Map<String, AlbumConnection> albums = archiveConnection.listAlbums();
					// remove invisible albums
					visibleAlbumsModified |= putIfNotExists(visibleAlbums, archiveName, new ConcurrentHashMap<String, String>()).keySet().retainAll(albums.keySet());

					final AtomicInteger albumCounter = new AtomicInteger();
					builder.setContentText("Downloading from " + archiveName);
					for (final Entry<String, AlbumConnection> albumEntry : albums.entrySet()) {
						if (!running.get()) {
							break;
						}
						final String albumName = albumEntry.getKey();
						final AlbumConnection albumConnection = albumEntry.getValue();
						updateDetailRunnables.add(new Callable<Void>() {
							@Override
							public Void call() throws Exception {
								try {
									updateAlbumDetail(archiveName, albumName, albumConnection, albums.size(), albumCounter);
								} catch (final Throwable t) {
									Log.w(SERVICE_TAG, "Exception while updateing data", t);
								}
								return null;
							}
						});
					}
					updateDetailRunnables.add(new Callable<Void>() {
						@Override
						public Void call() throws Exception {
							return callInTransaction(new Callable<Void>() {
								@Override
								public Void call() throws Exception {
									final ArchiveMeta foundStorages = archiveConnection.listStorages();
									if (foundStorages == null) {
										return null;
									}
									final DataProvider currentDataProvider = getCurrentDataProvider();
									final BTreeMap<String, ArchiveMeta> archiveMetaMap = currentDataProvider.getArchiveMetaMap();
									final boolean modified = !ObjectUtils.objectEquals(archiveMetaMap.get(archiveName), foundStorages);
									if (modified) {
										archiveMetaMap.put(archiveName, foundStorages);
										cursorNotifications.notifyStoragesModified();
									}
									final String newVersion = foundStorages.getVersion();
									final BTreeMap<AlbumIndex, AlbumMutationData> currentAlbumMutationMap = currentDataProvider.getAlbumMutationDataMap();
									for (final Entry<AlbumIndex, AlbumMutationData> albumEntry : currentAlbumMutationMap.entrySet()) {
										final AlbumMutationData mutationData = albumEntry.getValue();
										if (mutationData != null) {
											final ArrayList<Mutation> mutations = new ArrayList<Mutation>(mutationData.getMutations());
											final AlbumIndex key = albumEntry.getKey();
											for (final Iterator<Mutation> mutationIterator = mutations.iterator(); mutationIterator.hasNext();) {
												final Mutation mutation = mutationIterator.next();
												if (mutation instanceof MetadataMutation) {
													final MetadataMutation metaMutation = (MetadataMutation) mutation;
													if (!metaMutation.getMetadataVersion().equals(newVersion)) {
														cursorNotifications.notifySingleAlbumCursorChanged(key);
														mutationIterator.remove();
													}
												}
											}
											mutations.trimToSize();
											currentAlbumMutationMap.put(key, new AlbumMutationData(mutations));
										}
									}
									return null;
								}
							}, false);
						}
					});
				}
				if (visibleAlbumsModified) {
					notifyAlbumListChanged();
				}
				final Collection<Future<Void>> invokeResults = CallbackUtil.limitConcurrent(wrappedExecutorService, 5, updateDetailRunnables);
				for (final Future<Void> future : invokeResults) {
					try {
						future.get();
					} catch (final Throwable t) {
						Log.e(SERVICE_TAG, "Exception while updateing data", t);
					}
				}
			} catch (final Throwable t) {
				Log.e(SERVICE_TAG, "Exception while updateing data", t);
			} finally {
				updateLockSempahore.release();
				notificationManager.notify(NOTIFICATION, makeNotificationBuilder().getNotification());
			}
		}
	}

	private void updateServerCursors() {
		cursorNotifications.notifyServerStateModified();
	}

}
