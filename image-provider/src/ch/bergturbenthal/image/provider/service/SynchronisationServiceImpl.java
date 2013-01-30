package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.io.FilenameFilter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.data.model.state.Issue;
import ch.bergturbenthal.image.data.model.state.Progress;
import ch.bergturbenthal.image.data.util.ExecutorServiceUtil;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.FieldReader;
import ch.bergturbenthal.image.provider.map.MapperUtil;
import ch.bergturbenthal.image.provider.map.NumericFieldReader;
import ch.bergturbenthal.image.provider.map.StringFieldReader;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryKeywordEntry;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.model.dto.AlbumDto;
import ch.bergturbenthal.image.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.service.MDnsListener.ResultListener;
import ch.bergturbenthal.image.provider.state.ServerListActivity;

import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;

public class SynchronisationServiceImpl extends Service implements ResultListener, SynchronisationService {

  /**
   * Class used for the client Binder. Because we know this service always runs
   * in the same process as its clients, we don't need to deal with IPC.
   */
  public class LocalBinder extends Binder {
    public SynchronisationService getService() {
      // Return this instance of LocalService so clients can call public methods
      return SynchronisationServiceImpl.this;
    }
  }

  private static final String THUMBNAIL_SUFFIX = ".thumbnail";

  // Binder given to clients
  private final IBinder binder = new LocalBinder();
  private final static String SERVICE_TAG = "Synchronisation Service";
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Map<String, ArchiveConnection>> connectionMap =
                                                                                new AtomicReference<Map<String, ArchiveConnection>>(
                                                                                                                                    Collections.<String, ArchiveConnection> emptyMap());

  private MDnsListener dnsListener;
  private ScheduledThreadPoolExecutor executorService;
  private final int NOTIFICATION = 0;

  private NotificationManager notificationManager;
  private ScheduledFuture<?> slowUpdatePollingFuture = null;
  private ExecutorService wrappedExecutorService;
  private File tempDir;
  private File thumbnailsTempDir;
  private DaoHolder daoHolder;
  private final ConcurrentMap<String, ConcurrentMap<String, Integer>> visibleAlbums = new ConcurrentHashMap<String, ConcurrentMap<String, Integer>>();

  private final CursorNotification cursorNotifications = new CursorNotification();
  private ScheduledFuture<?> fastUpdatePollingFuture;

  private final Semaphore updateLockSempahore = new Semaphore(1);

  private LruCache<Integer, File> thumbnailCache;

  private File thumbnailsSyncDir;

  private final LruCache<String, Long> idCache = new LruCache<String, Long>(100) {

    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    protected Long create(final String key) {
      return idGenerator.incrementAndGet();
    }

  };

  @Override
  public File getLoadedThumbnail(final int thumbnailId) {
    return thumbnailCache.get(Integer.valueOf(thumbnailId));
  }

  @Override
  public void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints, final boolean withProgressUpdate) {
    for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
      Log.i(SERVICE_TAG, "Addr: " + inetSocketAddress);
    }
    final Map<String, Map<URL, PingResponse>> pingResponses = new HashMap<String, Map<URL, PingResponse>>();
    for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
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
      final ArchiveConnection connection =
                                           oldConnectionMap.containsKey(archiveId) ? oldConnectionMap.get(archiveId)
                                                                                  : new ArchiveConnection(archiveId, wrappedExecutorService);
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

    executorService = new ScheduledThreadPoolExecutor(4, new ThreadFactory() {
      final AtomicInteger nextThreadIndex = new AtomicInteger(0);

      @Override
      public Thread newThread(final Runnable r) {
        return new Thread(r, "synchronisation-worker-" + nextThreadIndex.getAndIncrement());
      }
    });

    registerScreenOnOff();
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    wrappedExecutorService = ExecutorServiceUtil.wrap(executorService);

    final ConnectionSource connectionSource = DatabaseHelper.makeConnectionSource(getApplicationContext());
    daoHolder = new DaoHolder(connectionSource);

    dnsListener = new MDnsListener(getApplicationContext(), this, executorService);

    // setup and clean temp-dir
    tempDir = new File(getCacheDir(), "temp");
    if (!tempDir.exists())
      tempDir.mkdirs();
    for (final File file : tempDir.listFiles()) {
      file.delete();
    }

    // setup thumbails-dir
    // temporary files
    thumbnailsTempDir = new File(getCacheDir(), "thumbnails");
    if (!thumbnailsTempDir.exists())
      thumbnailsTempDir.mkdirs();
    // explicit synced thumbnails
    thumbnailsSyncDir = new File(getFilesDir(), "thumbnails");
    if (!thumbnailsSyncDir.exists())
      thumbnailsSyncDir.mkdirs();
    // preload thumbnail-cache
    initThumbnailCache(512 * 1024);

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
      if (command != null)
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
    return START_STICKY;
  }

  @Override
  public Cursor readAlbumEntryList(final int albumId, final String[] projection) {
    return daoHolder.callInTransaction(new Callable<Cursor>() {

      @Override
      public Cursor call() throws Exception {
        final QueryBuilder<AlbumEntryEntity, Integer> queryBuilder = getAlbumEntryDao().queryBuilder();
        queryBuilder.where().eq("album_id", getAlbumDao().queryForId(Integer.valueOf(albumId))).and().eq("deleted", Boolean.FALSE);
        queryBuilder.orderBy("captureDate", true);

        return makeCursorForAlbumEntries(queryBuilder, projection, albumId);
      }
    });
  }

  @Override
  public Cursor readAlbumList(final String[] projection) {
    return daoHolder.callInTransaction(new Callable<Cursor>() {

      @Override
      public Cursor call() throws Exception {
        return makeCursorForAlbums(collectVisibleAlbums(), projection, true);
      }

    });
  }

  @Override
  public Cursor readServerIssueList(final String serverId, final String[] projection) {

    final ServerConnection serverConnection = getConnectionForServer(serverId);
    if (serverConnection == null)
      return null;
    final Collection<Issue> progressValues = new ArrayList<Issue>(serverConnection.getServerState().getIssues());

    final Map<String, String> mappedFields = new HashMap<String, String>();
    mappedFields.put(Client.IssueEntry.CAN_ACK, "acknowledgable");
    mappedFields.put(Client.IssueEntry.ALBUM_NAME, "albumName");
    mappedFields.put(Client.IssueEntry.ALBUM_ENTRY_NAME, "imageName");
    mappedFields.put(Client.IssueEntry.ISSUE_TIME, "issueTime");
    mappedFields.put(Client.IssueEntry.STACK_TRACE, "stackTrace");
    mappedFields.put(Client.IssueEntry.ISSUE_TYPE, "type");

    final Map<String, FieldReader<Issue>> fieldReaders = MapperUtil.makeNamedFieldReaders(Issue.class, mappedFields);
    fieldReaders.put(Client.IssueEntry.ID, new NumericFieldReader<Issue>(Cursor.FIELD_TYPE_INTEGER) {
      @Override
      public Number getNumber(final Issue value) {
        return Long.valueOf(makeLongId(value.getIssueId()));
      }
    });
    return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(progressValues, projection, fieldReaders));
  }

  @Override
  public Cursor readServerList(final String[] projection) {
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

    return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(connections, projection, fieldReaders));

  }

  @Override
  public Cursor readServerProgresList(final String serverId, final String[] projection) {
    final ServerConnection serverConnection = getConnectionForServer(serverId);
    if (serverConnection == null)
      return null;
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
    return cursorNotifications.addStateCursor(MapperUtil.loadCollectionIntoCursor(progressValues, projection, fieldReaders));
  }

  @Override
  public Cursor readSingleAlbum(final int albumId, final String[] projection) {
    return daoHolder.callInTransaction(new Callable<Cursor>() {
      @Override
      public Cursor call() throws Exception {
        final Collection<Integer> visible = collectVisibleAlbums();
        if (visible.contains(Integer.valueOf(albumId)))
          return makeCursorForAlbums(Collections.singletonList(Integer.valueOf(albumId)), projection, false);
        else
          return makeCursorForAlbums(Collections.<Integer> emptyList(), projection, false);
      }
    });
  }

  @Override
  public Cursor readSingleAlbumEntry(final int albumId, final int albumEntryId, final String[] projection) {
    return daoHolder.callInTransaction(new Callable<Cursor>() {
      @Override
      public Cursor call() throws Exception {
        final QueryBuilder<AlbumEntryEntity, Integer> queryBuilder = getAlbumEntryDao().queryBuilder();
        queryBuilder.where().idEq(albumEntryId).and().eq("deleted", Boolean.FALSE);
        return makeCursorForAlbumEntries(queryBuilder, projection, albumId);
      }
    });
  }

  @Override
  public int updateAlbum(final int albumId, final ContentValues values) {
    return callInTransaction(new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
        final AlbumEntity albumEntity = albumDao.queryForId(Integer.valueOf(albumId));
        if (albumEntity == null)
          return Integer.valueOf(0);
        final Boolean shouldSync = values.getAsBoolean(Client.Album.SHOULD_SYNC);
        if (shouldSync != null)
          if (albumEntity.isShouldSync() != shouldSync.booleanValue()) {
            albumEntity.setShouldSync(shouldSync.booleanValue());
            if (!shouldSync)
              albumEntity.setSynced(false);
            albumDao.update(albumEntity);
            notifyAlbumChanged(albumId);
          }
        return Integer.valueOf(1);
      }
    }).intValue();
  }

  @Override
  public int updateAlbumEntry(final int albumId, final int albumEntryId, final ContentValues values) {
    return callInTransaction(new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();
        final AlbumEntryEntity albumEntryEntity = albumEntryDao.queryForId(Integer.valueOf(albumEntryId));
        if (albumEntryEntity == null)
          return Integer.valueOf(0);
        boolean modified = false;
        if (values.containsKey(Client.AlbumEntry.META_RATING)
            && !objectEquals(values.getAsInteger(Client.AlbumEntry.META_RATING), albumEntryEntity.getMetaRating())) {
          albumEntryEntity.setMetaRating(values.getAsInteger(Client.AlbumEntry.META_RATING));
          albumEntryEntity.setMetaRatingModified(true);
          modified = true;
        }
        if (values.containsKey(Client.AlbumEntry.META_CAPTION)
            && !objectEquals(values.getAsString(Client.AlbumEntry.META_CAPTION), albumEntryEntity.getMetaCaption())) {
          albumEntryEntity.setMetaCaption(values.getAsString(Client.AlbumEntry.META_CAPTION));
          albumEntryEntity.setMetaCaptionModified(true);
          modified = true;
        }
        if (values.containsKey(Client.AlbumEntry.META_KEYWORDS)) {
          final RuntimeExceptionDao<AlbumEntryKeywordEntry, Integer> keywordDao = getKeywordDao();
          final Collection<String> remainingKeywords =
                                                       new HashSet<String>(
                                                                           Client.AlbumEntry.decodeKeywords(values.getAsString(Client.AlbumEntry.META_KEYWORDS)));
          for (final AlbumEntryKeywordEntry keywordEntry : albumEntryEntity.getKeywords()) {
            keywordDao.refresh(keywordEntry);
            final boolean keepThisKeyword = remainingKeywords.remove(keywordEntry.getKeyword());
            if (keepThisKeyword) {
              if (keywordEntry.isDeleted()) {
                keywordEntry.setDeleted(false);
                keywordDao.update(keywordEntry);
              }
            } else {
              if (!keywordEntry.isDeleted()) {
                keywordEntry.setDeleted(true);
                keywordDao.update(keywordEntry);
              }
            }
          }
          for (final String remainingKeyword : remainingKeywords) {
            final AlbumEntryKeywordEntry newKeyword = new AlbumEntryKeywordEntry(albumEntryEntity, remainingKeyword);
            newKeyword.setAdded(true);
            keywordDao.create(newKeyword);
          }
        }
        if (modified) {
          albumEntryDao.update(albumEntryEntity);
        }
        return Integer.valueOf(1);
      }
    }).intValue();
  }

  private <V> V callInTransaction(final Callable<V> callable) {
    return cursorNotifications.doWithNotify(callable);
  }

  private Collection<Integer> collectVisibleAlbums() {
    final Collection<Integer> ret = new LinkedHashSet<Integer>();
    for (final ConcurrentMap<String, Integer> archive : visibleAlbums.values()) {
      ret.addAll(archive.values());
    }
    return ret;
  }

  private boolean dateEquals(final Date date1, final Date date2) {
    if (date1 == null)
      return date2 == null;
    if (date2 == null)
      return false;
    return Math.abs(date1.getTime() - date2.getTime()) < 1000;
  }

  private List<AlbumEntity> findAlbumByArchiveAndName(final ArchiveEntity archiveEntity, final String name) {
    final Map<String, Object> valueMap = new HashMap<String, Object>();
    valueMap.put("archive_id", archiveEntity);
    valueMap.put("name", name);
    return getAlbumDao().queryForFieldValues(valueMap);
  }

  private RuntimeExceptionDao<AlbumEntity, Integer> getAlbumDao() {
    return daoHolder.getDao(AlbumEntity.class);
  }

  private RuntimeExceptionDao<AlbumEntryEntity, Integer> getAlbumEntryDao() {
    return daoHolder.getDao(AlbumEntryEntity.class);
  }

  private RuntimeExceptionDao<ArchiveEntity, String> getArchiveDao() {
    return daoHolder.getDao(ArchiveEntity.class);
  }

  private ServerConnection getConnectionForServer(final String serverId) {
    final Map<String, ArchiveConnection> archives = connectionMap.get();

    ServerConnection serverConnection = null;
    for (final Entry<String, ArchiveConnection> archiveEntry : archives.entrySet()) {
      for (final ServerConnection server : archiveEntry.getValue().listServers().values()) {
        if (server.getInstanceId().equals(serverId))
          serverConnection = server;
      }
    }
    return serverConnection;
  }

  private RuntimeExceptionDao<AlbumEntryKeywordEntry, Integer> getKeywordDao() {
    return daoHolder.getDao(AlbumEntryKeywordEntry.class);
  }

  private File ifExsists(final File file) {
    return file.exists() ? file : null;
  }

  /**
   * 
   * initializes thumbnail-cache
   * 
   * @param size
   *          Cache-Size in kB
   */
  private void initThumbnailCache(final int size) {
    thumbnailCache = new LruCache<Integer, File>(size) {

      @Override
      protected File create(final Integer key) {
        return loadThumbnail(key.intValue());
      }

      @Override
      protected void entryRemoved(final boolean evicted, final Integer key, final File oldValue, final File newValue) {
        if (!thumbnailsTempDir.equals(oldValue.getParentFile()))
          return;
        final boolean deleted = oldValue.delete();
        if (!deleted)
          throw new RuntimeException("Cannot delete cache-file " + oldValue);
      }

      @Override
      protected int sizeOf(final Integer key, final File value) {
        if (!thumbnailsTempDir.equals(value.getParentFile()))
          // count only temporary entries
          return 0;
        return (int) value.length() / 1024;
      }
    };
    for (final String filename : thumbnailsTempDir.list(new FilenameFilter() {
      @Override
      public boolean accept(final File dir, final String filename) {
        return filename.endsWith(THUMBNAIL_SUFFIX);
      }
    })) {
      final File loadedFile = thumbnailCache.get(Integer.valueOf(filename.substring(0, filename.length() - THUMBNAIL_SUFFIX.length())));
      // delete obsolete cache-entry
      if (loadedFile == null) {
        new File(thumbnailsTempDir, filename).delete();
      }
    }
  }

  private String lastPart(final String[] split) {
    if (split == null || split.length == 0)
      return null;
    return split[split.length - 1];
  }

  private File loadThumbnail(final int thumbnailId) {
    final AlbumEntryEntity albumEntryEntity = readAlbumEntry(thumbnailId);
    if (albumEntryEntity == null)
      return null;
    final boolean permanentDownload = albumEntryEntity.getAlbum().isShouldSync();

    final File temporaryTargetFile = new File(thumbnailsTempDir, thumbnailId + THUMBNAIL_SUFFIX);
    final File permanentTargetFile = new File(thumbnailsSyncDir, thumbnailId + THUMBNAIL_SUFFIX);
    final File targetFile = permanentDownload ? permanentTargetFile : temporaryTargetFile;
    final File otherTargetFile = permanentDownload ? temporaryTargetFile : permanentTargetFile;
    // check if the file in the current cache is valid
    if (targetFile.exists() && targetFile.lastModified() >= albumEntryEntity.getLastModified().getTime()) {
      if (otherTargetFile.exists())
        otherTargetFile.delete();
      return targetFile;
    }
    // check if there is a valid file in the other cache
    if (otherTargetFile.exists()) {
      if (otherTargetFile.lastModified() >= albumEntryEntity.getLastModified().getTime()) {
        final long oldLastModified = otherTargetFile.lastModified();
        otherTargetFile.renameTo(targetFile);
        targetFile.setLastModified(oldLastModified);
        if (targetFile.exists())
          return targetFile;
      }
      // remove the invalid file of the other cache
      otherTargetFile.delete();
    }
    final Map<String, ArchiveConnection> archive = connectionMap.get();
    if (archive == null)
      return ifExsists(targetFile);
    final ArchiveConnection archiveConnection = archive.get(albumEntryEntity.getAlbum().getArchive().getName());
    if (archiveConnection == null)
      return ifExsists(targetFile);
    final AlbumConnection albumConnection = archiveConnection.getAlbums().get(albumEntryEntity.getAlbum().getName());
    if (albumConnection == null)
      return ifExsists(targetFile);
    final File tempFile = new File(tempDir, thumbnailId + ".thumbnail-temp");
    try {
      albumConnection.readThumbnail(albumEntryEntity.getCommId(), tempFile, targetFile);
    } finally {
      if (tempFile.exists())
        tempFile.delete();
    }
    return ifExsists(targetFile);
  }

  private void loadThumbnailsOfAlbum(final int albumId) {
    final Collection<Integer> thumbnails = callInTransaction(new Callable<Collection<Integer>>() {

      @Override
      public Collection<Integer> call() throws Exception {
        final ArrayList<Integer> ret = new ArrayList<Integer>();
        for (final AlbumEntryEntity albumEntryEntity : getAlbumDao().queryForId(Integer.valueOf(albumId)).getEntries()) {
          ret.add(Integer.valueOf(albumEntryEntity.getId()));
        }
        return ret;
      }
    });
    for (final Integer thumbnailId : thumbnails) {
      loadThumbnail(thumbnailId);
    }
    callInTransaction(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
        final AlbumEntity entity = albumDao.queryForId(Integer.valueOf(albumId));
        if (!entity.isSynced()) {
          entity.setSynced(true);
          albumDao.update(entity);
        }
        return null;
      }
    });
  }

  private Cursor makeCursorForAlbumEntries(final QueryBuilder<AlbumEntryEntity, Integer> queryBuilder, final String[] projection, final int albumId) {
    final Map<String, FieldReader<AlbumEntryEntity>> fieldReaders = MapperUtil.makeAnnotaedFieldReaders(AlbumEntryEntity.class);

    fieldReaders.put(Client.AlbumEntry.THUMBNAIL, new StringFieldReader<AlbumEntryEntity>() {

      @Override
      public String getString(final AlbumEntryEntity value) {
        return Client.makeThumbnailUri(albumId, value.getId()).toString();
      }
    });
    fieldReaders.put(Client.AlbumEntry.META_KEYWORDS, new StringFieldReader<AlbumEntryEntity>() {

      @Override
      public String getString(final AlbumEntryEntity value) {
        final Collection<String> keywordList = new ArrayList<String>();
        final Collection<AlbumEntryKeywordEntry> keywords = value.getKeywords();
        for (final AlbumEntryKeywordEntry albumEntryKeywordEntry : keywords) {
          if (!albumEntryKeywordEntry.isDeleted())
            keywordList.add(albumEntryKeywordEntry.getKeyword());
        }
        return Client.AlbumEntry.encodeKeywords(keywordList);
      }
    });
    fieldReaders.put(Client.AlbumEntry.ENTRY_URI, new StringFieldReader<AlbumEntryEntity>() {

      @Override
      public String getString(final AlbumEntryEntity value) {
        return Client.makeAlbumEntryUri(albumId, value.getId()).toString();
      }
    });

    return cursorNotifications.addSingleAlbumCursor(albumId, MapperUtil.loadQueryIntoCursor(queryBuilder, projection, fieldReaders));
  }

  private Cursor makeCursorForAlbums(final Collection<Integer> visibleAlbums, final String[] projection, final boolean alsoSynced)
                                                                                                                                  throws SQLException {
    final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
    final QueryBuilder<AlbumEntity, Integer> albumEntityBuilder = albumDao.queryBuilder();
    albumEntityBuilder.orderBy("albumCaptureDate", false);
    if (!alsoSynced)
      albumEntityBuilder.where().in("id", visibleAlbums);
    else
      albumEntityBuilder.where().eq("synced", Boolean.TRUE).or().in("id", visibleAlbums);

    final List<AlbumEntity> albums = albumEntityBuilder.query();

    final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();

    final QueryBuilder<AlbumEntryEntity, Integer> summaryFieldsBuilder = albumEntryDao.queryBuilder();
    summaryFieldsBuilder.where().in("album_id", albums).and().eq("deleted", Boolean.FALSE);
    summaryFieldsBuilder.groupBy("album_id");
    summaryFieldsBuilder.selectRaw("album_id", "count(*)", "sum(originalSize)", "sum(thumbnailSize)");

    final String statement = summaryFieldsBuilder.prepare().getStatement();

    final GenericRawResults<String[]> summaryRows = albumEntryDao.queryRaw(statement);
    final Map<String, String[]> summaryResult = new HashMap<String, String[]>();
    for (final String[] resultRow : summaryRows.getResults()) {
      summaryResult.put(resultRow[0], resultRow);
    }

    final Map<String, FieldReader<AlbumEntity>> fieldReaders = MapperUtil.makeAnnotaedFieldReaders(AlbumEntity.class);
    fieldReaders.put(Client.Album.ARCHIVE_NAME, new StringFieldReader<AlbumEntity>() {
      @Override
      public String getString(final AlbumEntity value) {
        if (value == null || value.getArchive() == null)
          return null;
        return value.getArchive().getName();
      }
    });
    fieldReaders.put(Client.Album.ENTRY_COUNT, new NumericFieldReader<AlbumEntity>(Cursor.FIELD_TYPE_INTEGER) {
      @Override
      public Number getNumber(final AlbumEntity value) {
        final String values[] = summaryResult.get(String.valueOf(value.getId()));
        if (values == null)
          return Integer.valueOf(0);
        return Integer.valueOf(values[1]);
      }
    });
    fieldReaders.put(Client.Album.ORIGINALS_SIZE, new NumericFieldReader<AlbumEntity>(Cursor.FIELD_TYPE_INTEGER) {
      @Override
      public Number getNumber(final AlbumEntity value) {
        final String[] sizeValue = summaryResult.get(String.valueOf(value.getId()));
        if (sizeValue == null)
          return Long.valueOf(0);
        return Long.valueOf(sizeValue[2]);
      }
    });
    fieldReaders.put(Client.Album.THUMBNAILS_SIZE, new NumericFieldReader<AlbumEntity>(Cursor.FIELD_TYPE_INTEGER) {
      @Override
      public Number getNumber(final AlbumEntity value) {
        final String[] sizeValue = summaryResult.get(String.valueOf(value.getId()));
        if (sizeValue == null)
          return Long.valueOf(0);
        return Long.valueOf(sizeValue[3]);
      }
    });
    fieldReaders.put(Client.Album.THUMBNAIL, new StringFieldReader<AlbumEntity>() {
      @Override
      public String getString(final AlbumEntity value) {
        if (value.getThumbnail() == null)
          return null;
        getAlbumEntryDao().refresh(value.getThumbnail());
        return Client.makeThumbnailUri(value.getId(), value.getThumbnail().getId()).toString();
      }
    });
    fieldReaders.put(Client.Album.ENTRY_URI, new StringFieldReader<AlbumEntity>() {
      @Override
      public String getString(final AlbumEntity value) {
        return Client.makeAlbumUri(value.getId()).toString();
      }
    });

    return cursorNotifications.addAllAlbumCursor(MapperUtil.loadCollectionIntoCursor(albums, projection, fieldReaders));
  }

  private long makeLongId(final String stringId) {
    return idCache.get(stringId).longValue();
  }

  private Builder makeNotificationBuilder() {
    final PendingIntent intent =
                                 PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), ServerListActivity.class),
                                                           0);
    final Builder builder =
                            new Notification.Builder(this).setContentTitle("Syncing").setSmallIcon(android.R.drawable.ic_dialog_info)
                                                          .setContentIntent(intent);
    return builder;
  }

  private URL makeUrl(final InetSocketAddress inetSocketAddress) {
    try {
      return new URL("http", inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort(), "rest");
    } catch (final MalformedURLException e) {
      throw new RuntimeException("Cannot create URL for Socket " + inetSocketAddress, e);
    }
  }

  private void notifyAlbumChanged(final int id) {
    cursorNotifications.notifySingleAlbumCursorChanged(id);
  }

  private void notifyAlbumListChanged() {
    cursorNotifications.notifyAllAlbumCursorsChanged();
  }

  private <O> boolean objectEquals(final O v1, final O v2) {
    if (v1 == v2)
      return true;
    if (v1 == null || v2 == null)
      return false;
    return v1.equals(v2);
  }

  private PingResponse pingService(final URL url) {
    final RestTemplate restTemplate = new RestTemplate(true);
    try {
      try {
        final ResponseEntity<PingResponse> entity = restTemplate.getForEntity(url + "/ping.json", PingResponse.class);
        final boolean pingOk = entity.getStatusCode().series() == Series.SUCCESSFUL;
        if (pingOk)
          return entity.getBody();
        else {
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
        } else
          throw ex;
      } catch (final RestClientException ex) {
        Log.d(SERVICE_TAG, "Connect to " + url + "/ failed, try more");
        return null;
      }
    } catch (final Exception ex) {
      throw new RuntimeException("Cannot connect to " + url, ex);
    }
  }

  private void pollServers() {
    try {
      final MDnsListener listener = dnsListener;
      if (listener != null) {
        listener.pollForServices(true);
      }
    } catch (final Throwable t) {
      Log.w(SERVICE_TAG, "Exception while polling", t);
    }
  }

  private <K, V> V putIfNotExists(final ConcurrentMap<K, V> map, final K key, final V emptyValue) {
    final V existingValue = map.putIfAbsent(key, emptyValue);
    if (existingValue != null)
      return existingValue;
    return emptyValue;
  }

  private AlbumEntryEntity readAlbumEntry(final int entryId) {
    return callInTransaction(new Callable<AlbumEntryEntity>() {

      @Override
      public AlbumEntryEntity call() throws Exception {
        final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
        final AlbumEntryEntity albumEntryEntity = albumEntryDao.queryForId(Integer.valueOf(entryId));
        if (albumEntryEntity == null)
          return null;
        albumDao.refresh(albumEntryEntity.getAlbum());
        return albumEntryEntity;
      }

    });
  }

  private void refreshAlbumDetail(final AlbumConnection albumConnection, final Integer albumId) {
    // read data from Server
    final AlbumDto albumDto = albumConnection.getAlbumDetail();
    callInTransaction(new Callable<Void>() {

      @Override
      public Void call() throws Exception {
        final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();

        final AtomicLong dateSum = new AtomicLong(0);
        final AtomicInteger dateCount = new AtomicInteger(0);
        final Map<String, AlbumEntryEntity> existingEntries = new HashMap<String, AlbumEntryEntity>();

        final AlbumEntity albumEntity = albumDao.queryForId(albumId);

        if (!dateEquals(albumEntity.getAutoAddDate(), albumDto.getAutoAddDate())
            || !dateEquals(albumEntity.getLastModified(), albumDto.getLastModified())) {
          albumEntity.setAutoAddDate(albumDto.getAutoAddDate());
          albumEntity.setLastModified(albumDto.getLastModified());
          albumDao.update(albumEntity);
          notifyAlbumListChanged();
        }
        final Collection<AlbumEntryEntity> entries = albumEntity.getEntries();
        for (final AlbumEntryEntity albumEntryEntity : entries) {
          existingEntries.put(albumEntryEntity.getName(), albumEntryEntity);
        }
        AlbumEntryEntity thumbnailCandidate = null;
        for (final Entry<String, AlbumEntryDto> albumImageEntry : albumDto.getEntries().entrySet()) {
          final AlbumEntryEntity existingEntry = existingEntries.remove(albumImageEntry.getKey());
          final AlbumEntryDto entryDto = albumImageEntry.getValue();

          if (existingEntry == null) {
            final AlbumEntryEntity albumEntryEntity =
                                                      new AlbumEntryEntity(albumEntity, albumImageEntry.getKey(), entryDto.getCommId(),
                                                                           entryDto.getEntryType(), entryDto.getLastModified(),
                                                                           entryDto.getCaptureDate());
            albumEntryEntity.setOriginalSize(entryDto.getOriginalFileSize());
            if (entryDto.getThumbnailSize() != null)
              albumEntryEntity.setThumbnailSize(entryDto.getThumbnailSize());
            if (albumEntryEntity.getCaptureDate() != null) {
              dateCount.incrementAndGet();
              dateSum.addAndGet(albumEntryEntity.getCaptureDate().getTime());
            }
            if (thumbnailCandidate == null) {
              thumbnailCandidate = albumEntryEntity;
            }
            if (updateEntity(albumEntryEntity, entryDto, true))
              albumEntryDao.create(albumEntryEntity);
            notifyAlbumChanged(albumEntity.getId());
          } else {
            if (thumbnailCandidate == null)
              thumbnailCandidate = existingEntry;
            final boolean modified = updateEntity(existingEntry, entryDto, false);

            if (modified) {
              albumEntryDao.update(existingEntry);
              notifyAlbumChanged(albumEntity.getId());
            }
            final Date captureDate = existingEntry.getCaptureDate();
            if (captureDate != null) {
              dateCount.incrementAndGet();
              dateSum.addAndGet(captureDate.getTime());
            }
          }
        }
        // set remaining entries to deleted
        for (final AlbumEntryEntity remainingEntry : existingEntries.values()) {
          if (!remainingEntry.isDeleted()) {
            remainingEntry.setDeleted(true);
            albumEntryDao.update(remainingEntry);
            notifyAlbumChanged(albumEntity.getId());
          }
        }
        boolean findNewThumbnail;
        final AlbumEntryEntity thumbnail = albumEntity.getThumbnail();
        if (thumbnail != null) {
          albumEntryDao.refresh(thumbnail);
          findNewThumbnail = thumbnail.isDeleted();
        } else
          findNewThumbnail = true;
        final Date middleCaptureDate = dateCount.get() == 0 ? null : new Date(dateSum.longValue() / dateCount.longValue());
        if (!dateEquals(middleCaptureDate, albumEntity.getAlbumCaptureDate()) || findNewThumbnail && thumbnailCandidate != null) {
          albumEntity.setAlbumCaptureDate(middleCaptureDate);
          if (findNewThumbnail && thumbnailCandidate != null)
            albumEntity.setThumbnail(thumbnailCandidate);
          albumDao.update(albumEntity);
          notifyAlbumListChanged();
        }
        return null;
      }

      private boolean updateEntity(final AlbumEntryEntity existingEntry, final AlbumEntryDto entryDto, final boolean mustCreate) {
        boolean modified = mustCreate;
        if (!dateEquals(existingEntry.getCaptureDate(), entryDto.getCaptureDate()) && entryDto.getCaptureDate() != null) {
          existingEntry.setCaptureDate(entryDto.getCaptureDate());
          modified = true;
        }
        if (existingEntry.getOriginalSize() != entryDto.getOriginalFileSize()) {
          existingEntry.setOriginalSize(entryDto.getOriginalFileSize());
          modified = true;
        }
        if (!objectEquals(existingEntry.getThumbnailSize(), entryDto.getThumbnailSize()) && entryDto.getThumbnailSize() != null) {
          existingEntry.setThumbnailSize(entryDto.getThumbnailSize());
          modified = true;
        }
        if (!objectEquals(existingEntry.getCameraMake(), entryDto.getCameraMake())) {
          existingEntry.setCameraMake(entryDto.getCameraMake());
          modified = true;
        }
        if (!objectEquals(existingEntry.getCameraModel(), entryDto.getCameraModel())) {
          existingEntry.setCameraMake(entryDto.getCameraModel());
          modified = true;
        }
        if (!objectEquals(existingEntry.getExposureTime(), entryDto.getExposureTime())) {
          existingEntry.setExposureTime(entryDto.getExposureTime());
          modified = true;
        }
        if (!objectEquals(existingEntry.getfNumber(), entryDto.getfNumber())) {
          existingEntry.setfNumber(entryDto.getfNumber());
          modified = true;
        }
        if (!objectEquals(existingEntry.getFocalLength(), entryDto.getFocalLength())) {
          existingEntry.setFocalLength(entryDto.getFocalLength());
          modified = true;
        }
        if (!objectEquals(existingEntry.getIso(), entryDto.getIso())) {
          existingEntry.setIso(entryDto.getIso());
          modified = true;
        }

        if (!objectEquals(existingEntry.getEditableMetadataHash(), entryDto.getEditableMetadataHash())) {
          existingEntry.setMetaCaption(entryDto.getCaption());
          existingEntry.setMetaCaptionModified(false);
          existingEntry.setMetaRating(entryDto.getRating());
          existingEntry.setMetaRatingModified(false);

          // remove and reset existing entries
          final RuntimeExceptionDao<AlbumEntryKeywordEntry, Integer> keywordDao = getKeywordDao();
          final Collection<String> remainingKeywords = new HashSet<String>(entryDto.getKeywords());
          for (final AlbumEntryKeywordEntry keywordEntry : existingEntry.getKeywords()) {
            final boolean found = remainingKeywords.remove(keywordEntry.getKeyword());
            if (found) {
              if (keywordEntry.isAdded() || keywordEntry.isDeleted()) {
                keywordEntry.setAdded(false);
                keywordEntry.setDeleted(false);
                keywordDao.update(keywordEntry);
              }
            } else {
              keywordDao.delete(keywordEntry);
            }
          }
          // add remaining entries
          if (!remainingKeywords.isEmpty()) {
            if (mustCreate) {
              getAlbumEntryDao().create(existingEntry);
              modified = false;
            }
          }
          for (final String keyword : remainingKeywords) {
            keywordDao.create(new AlbumEntryKeywordEntry(existingEntry, keyword));
          }
          existingEntry.setEditableMetadataHash(entryDto.getEditableMetadataHash());
        }

        if (existingEntry.isDeleted()) {
          existingEntry.setDeleted(false);
          modified = true;
        }
        return modified;
      }

    });
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
    if (fastUpdatePollingFuture == null || fastUpdatePollingFuture.isCancelled())
      fastUpdatePollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {

        @Override
        public void run() {
          pollServers();
        }
      }, 2, 3, TimeUnit.SECONDS);

  }

  private synchronized void startRunning() {
    if (running.get())
      return;
    running.set(true);
    Log.i(SERVICE_TAG, "Synchronisation started");
    dnsListener.startListening();
    final Notification notification = makeNotificationBuilder().getNotification();
    notificationManager.notify(NOTIFICATION, notification);
    startSlowPolling();
  }

  private synchronized void startSlowPolling() {
    if (slowUpdatePollingFuture == null || slowUpdatePollingFuture.isCancelled())
      slowUpdatePollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          pollServers();
        }
      }, 10, 20, TimeUnit.MINUTES);

  }

  private synchronized void stopFastPolling() {
    if (fastUpdatePollingFuture != null)
      fastUpdatePollingFuture.cancel(false);
  }

  private synchronized void stopRunning() {
    dnsListener.stopListening();
    notificationManager.cancel(NOTIFICATION);
    stopSlowPolling();
    stopFastPolling();
    running.set(false);
  }

  private synchronized void stopSlowPolling() {
    if (slowUpdatePollingFuture != null)
      slowUpdatePollingFuture.cancel(false);
  }

  private void updateAlbumDetail(final String archiveName, final String albumName, final AlbumConnection albumConnection, final int totalAlbumCount,
                                 final AtomicInteger albumCounter) {
    final ConcurrentMap<String, Integer> visibleAlbumsOfArchive =
                                                                  putIfNotExists(visibleAlbums, archiveName, new ConcurrentHashMap<String, Integer>());

    final Notification.Builder builder = makeNotificationBuilder();
    builder.setContentTitle("DB Update");
    builder.setContentText("Downloading " + lastPart(albumName.split("/")) + " from " + archiveName);
    builder.setProgress(totalAlbumCount, albumCounter.incrementAndGet(), false);
    notificationManager.notify(NOTIFICATION, builder.getNotification());

    final AtomicInteger albumId = new AtomicInteger();
    final AtomicBoolean shouldUpdateMeta = new AtomicBoolean(false);
    final AtomicBoolean shouldLoadThumbnails = new AtomicBoolean(false);
    callInTransaction(new Callable<Void>() {

      @Override
      public Void call() throws Exception {
        final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = getArchiveDao();
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();

        final ArchiveEntity archiveEntity = archiveDao.queryForId(archiveName);

        final List<AlbumEntity> foundEntries = findAlbumByArchiveAndName(archiveEntity, albumName);
        final AlbumEntity albumEntity;
        if (foundEntries.isEmpty()) {
          albumEntity = new AlbumEntity(archiveEntity, albumName, albumConnection.getCommId());
          albumDao.create(albumEntity);
          notifyAlbumListChanged();
        } else {
          albumEntity = foundEntries.get(0);
        }
        visibleAlbumsOfArchive.put(albumName, Integer.valueOf(albumEntity.getId()));
        albumId.set(albumEntity.getId());
        shouldUpdateMeta.set(!dateEquals(albumEntity.getLastModified(), albumConnection.lastModified()));
        shouldLoadThumbnails.set(albumEntity.isShouldSync());
        return null;
      }
    });
    if (shouldUpdateMeta.get())
      refreshAlbumDetail(albumConnection, albumId.intValue());
    if (shouldLoadThumbnails.get())
      loadThumbnailsOfAlbum(albumId.intValue());
  }

  private void updateAlbumsOnDB() {
    final boolean hasLock = updateLockSempahore.tryAcquire();
    if (hasLock) {
      try {
        final Notification.Builder builder = makeNotificationBuilder();
        builder.setContentTitle("DB Update");
        notificationManager.notify(NOTIFICATION, builder.getNotification());

        // remove invisible archives
        visibleAlbums.keySet().retainAll(connectionMap.get().keySet());

        final Collection<Callable<Void>> updateDetailRunnables = new ArrayList<Callable<Void>>();

        for (final Entry<String, ArchiveConnection> archive : connectionMap.get().entrySet()) {
          if (!running.get())
            break;
          final String archiveName = archive.getKey();

          callInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = getArchiveDao();
              final ArchiveEntity loadedArchive = archiveDao.queryForId(archiveName);
              if (loadedArchive == null) {
                archiveDao.create(new ArchiveEntity(archiveName));
              }
              return null;
            }
          });
          final Map<String, AlbumConnection> albums = archive.getValue().listAlbums();
          // remove invisible albums
          putIfNotExists(visibleAlbums, archiveName, new ConcurrentHashMap<String, Integer>()).keySet().retainAll(albums.keySet());

          final AtomicInteger albumCounter = new AtomicInteger();
          builder.setContentText("Downloading from " + archiveName);
          for (final Entry<String, AlbumConnection> albumEntry : albums.entrySet()) {
            if (!running.get())
              break;
            final String albumName = albumEntry.getKey();
            final AlbumConnection albumConnection = albumEntry.getValue();
            updateDetailRunnables.add(new Callable<Void>() {
              @Override
              public Void call() throws Exception {
                try {
                  updateAlbumDetail(archiveName, albumName, albumConnection, albums.size(), albumCounter);
                } catch (final Throwable t) {
                  Log.e(SERVICE_TAG, "Exception while updateing data", t);
                }
                return null;
              }
            });
          }
        }
        wrappedExecutorService.invokeAll(updateDetailRunnables);
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
