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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
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
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.data.model.state.Progress;
import ch.bergturbenthal.image.data.util.ExecutorServiceUtil;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.FieldReader;
import ch.bergturbenthal.image.provider.map.MapperUtil;
import ch.bergturbenthal.image.provider.map.NumericFieldReader;
import ch.bergturbenthal.image.provider.map.StringFieldReader;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryEntity;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.model.dto.AlbumDto;
import ch.bergturbenthal.image.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.service.MDnsListener.ResultListener;
import ch.bergturbenthal.image.provider.state.ServerListActivity;

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
  private File thumbnailsDir;
  private DaoHolder daoHolder;
  private final ConcurrentMap<String, ConcurrentMap<String, Integer>> visibleAlbums = new ConcurrentHashMap<String, ConcurrentMap<String, Integer>>();

  private final CursorNotification cursorNotifications = new CursorNotification();
  private ScheduledFuture<?> fastUpdatePollingFuture;

  private final Semaphore updateLockSempahore = new Semaphore(1);

  private LruCache<Integer, File> thumbnailCache;

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
    executorService = new ScheduledThreadPoolExecutor(2);

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
    thumbnailsDir = new File(getCacheDir(), "thumbnails");
    if (!thumbnailsDir.exists())
      thumbnailsDir.mkdirs();

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
        queryBuilder.where().eq("album_id", getAlbumDao().queryForId(Integer.valueOf(albumId)));
        queryBuilder.orderBy("captureDate", true);

        final Map<String, FieldReader<AlbumEntryEntity>> fieldReaders = MapperUtil.makeAnnotaedFieldReaders(AlbumEntryEntity.class);

        fieldReaders.put(Client.AlbumEntry.THUMBNAIL, new StringFieldReader<AlbumEntryEntity>() {

          @Override
          public String getString(final AlbumEntryEntity value) {
            return Client.makeThumbnailUri(albumId, value.getId()).toString();
          }
        });

        return cursorNotifications.addSingleAlbumCursor(albumId, MapperUtil.loadQueryIntoCursor(queryBuilder, projection, fieldReaders));
      }
    });
  }

  @Override
  public Cursor readAlbumList(final String[] projection) {
    return daoHolder.callInTransaction(new Callable<Cursor>() {

      @Override
      public Cursor call() throws Exception {
        final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
        final QueryBuilder<AlbumEntity, Integer> queryBuilder = albumDao.queryBuilder();
        queryBuilder.orderBy("albumCaptureDate", false);

        queryBuilder.where().eq("synced", Boolean.TRUE).or().in("id", collectVisibleAlbums());

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

          private final WeakHashMap<AlbumEntity, Integer> cachedCount = new WeakHashMap<AlbumEntity, Integer>();

          @Override
          public Number getNumber(final AlbumEntity value) {
            final Integer cachedValue = cachedCount.get(value);
            if (cachedValue != null)
              return cachedValue;
            try {
              final QueryBuilder<AlbumEntryEntity, Integer> builder = getAlbumEntryDao().queryBuilder();
              builder.where().eq("album_id", value).and().eq("deleted", Boolean.FALSE);
              builder.setCountOf(true);
              final Integer result = Integer.valueOf(builder.queryRawFirst()[0]);
              cachedCount.put(value, result);
              return result;
            } catch (final SQLException e) {
              throw new RuntimeException("Cannot count entries of Album" + value, e);
            }
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

        return cursorNotifications.addAllAlbumCursor(MapperUtil.loadQueryIntoCursor(queryBuilder, projection, fieldReaders));
      }

    });
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
    final Map<String, ArchiveConnection> archives = connectionMap.get();

    ServerConnection serverConnection = null;
    for (final Entry<String, ArchiveConnection> archiveEntry : archives.entrySet()) {
      for (final ServerConnection server : archiveEntry.getValue().listServers().values()) {
        if (server.getInstanceId().equals(serverId))
          serverConnection = server;
      }
    }
    if (serverConnection == null)
      return null;
    final Collection<Progress> progressValues =
                                                new ArrayList<Progress>(serverConnection == null ? Collections.<Progress> emptyList()
                                                                                                : serverConnection.getServerState().getProgress());
    // final Progress dummyProgress = new Progress();
    // dummyProgress.setCurrentStepDescription("DummyStep");
    // dummyProgress.setCurrentStepNr(7);
    // dummyProgress.setStepCount(9);
    // dummyProgress.setProgressDescription("DummyProgress");
    // dummyProgress.setType(ProgressType.IMPORT_IMAGES);
    // dummyProgress.setProgressId(UUID.randomUUID().toString());
    // progressValues.add(dummyProgress);

    final Map<String, String> mappedFields = new HashMap<String, String>();
    // mappedFields.put(Client.ProgressEntry.ID, "progressId");
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

  private <V> V callInTransaction(final Callable<V> callable) {
    return cursorNotifications.doWithNotify(callable);
  }

  private Iterable<Integer> collectVisibleAlbums() {
    final ArrayList<Integer> ret = new ArrayList<Integer>();
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
    return date1.getTime() == date2.getTime();
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
        if (!thumbnailsDir.equals(oldValue.getParentFile()))
          return;
        final boolean deleted = oldValue.delete();
        if (!deleted)
          throw new RuntimeException("Cannot delete cache-file " + oldValue);
      }

      @Override
      protected int sizeOf(final Integer key, final File value) {
        if (!thumbnailsDir.equals(value.getParentFile()))
          // count only temporary entries
          return 0;
        return (int) value.length() / 1024;
      }
    };
    for (final String filename : thumbnailsDir.list(new FilenameFilter() {
      @Override
      public boolean accept(final File dir, final String filename) {
        return filename.endsWith(THUMBNAIL_SUFFIX);
      }
    })) {
      thumbnailCache.get(Integer.valueOf(filename.substring(0, filename.length() - THUMBNAIL_SUFFIX.length())));
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
    final File targetFile = new File(thumbnailsDir, thumbnailId + THUMBNAIL_SUFFIX);
    if (targetFile.exists() && targetFile.lastModified() >= albumEntryEntity.getLastModified().getTime()) {
      return targetFile;
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
    albumConnection.readThumbnail(albumEntryEntity.getCommId(), tempFile, targetFile);
    return ifExsists(targetFile);
  }

  private long makeLongId(final String stringId) {
    final UUID uuid = UUID.fromString(stringId);
    final long longId = uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits();
    return longId;
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

        for (final Entry<String, AlbumEntryDto> albumImageEntry : albumDto.getEntries().entrySet()) {
          final AlbumEntryEntity existingEntry = existingEntries.remove(albumImageEntry.getKey());
          final AlbumEntryDto entryDto = albumImageEntry.getValue();

          if (existingEntry == null) {
            final AlbumEntryEntity albumEntryEntity =
                                                      new AlbumEntryEntity(albumEntity, albumImageEntry.getKey(), entryDto.getCommId(),
                                                                           entryDto.getEntryType(), entryDto.getLastModified(),
                                                                           entryDto.getCaptureDate());
            albumEntryDao.create(albumEntryEntity);
            if (albumEntryEntity.getCaptureDate() != null) {
              dateCount.incrementAndGet();
              dateSum.addAndGet(albumEntryEntity.getCaptureDate().getTime());
            }
            if (albumEntity.getThumbnail() == null) {
              albumEntity.setThumbnail(albumEntryEntity);
              albumDao.update(albumEntity);
              notifyAlbumListChanged();
            }
            notifyAlbumChanged(albumEntity.getId());
          } else {
            if (!dateEquals(existingEntry.getCaptureDate(), entryDto.getCaptureDate()) && entryDto.getCaptureDate() != null
                || existingEntry.isDeleted()) {
              existingEntry.setCaptureDate(entryDto.getCaptureDate());
              existingEntry.setDeleted(false);
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
        final Date middleCaptureDate = dateCount.get() == 0 ? null : new Date(dateSum.longValue() / dateCount.longValue());
        if (!dateEquals(middleCaptureDate, albumEntity.getAlbumCaptureDate())) {
          albumEntity.setAlbumCaptureDate(middleCaptureDate);
          albumDao.update(albumEntity);
          notifyAlbumListChanged();
        }
        return null;
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

    final Integer albumId = callInTransaction(new Callable<Integer>() {

      @Override
      public Integer call() throws Exception {
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

        return dateEquals(albumEntity.getLastModified(), albumConnection.lastModified()) ? null : Integer.valueOf(albumEntity.getId());
      }
    });
    if (albumId != null)
      refreshAlbumDetail(albumConnection, albumId);
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
                updateAlbumDetail(archiveName, albumName, albumConnection, albums.size(), albumCounter);
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
