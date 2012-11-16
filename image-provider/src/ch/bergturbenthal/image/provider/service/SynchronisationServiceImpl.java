package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus.Series;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.provider.R;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryEntity;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.model.dto.AlbumDto;
import ch.bergturbenthal.image.provider.model.dto.AlbumEntryDto;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.service.MDnsListener.ResultListener;
import ch.bergturbenthal.image.provider.util.ExecutorServiceUtil;

import com.j256.ormlite.dao.RuntimeExceptionDao;
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

  // Binder given to clients
  private final IBinder binder = new LocalBinder();
  private final static String SERVICE_TAG = "Synchronisation Service";
  private static final String UPDATE_DB_NOTIFICATION = "UpdateDb";
  private final AtomicReference<Map<String, ArchiveConnection>> connectionMap =
                                                                                new AtomicReference<Map<String, ArchiveConnection>>(
                                                                                                                                    Collections.<String, ArchiveConnection> emptyMap());

  private MDnsListener dnsListener;
  private ScheduledThreadPoolExecutor executorService;
  private final int NOTIFICATION = R.string.synchronisation_service_started;

  private NotificationManager notificationManager;
  private ScheduledFuture<?> pollingFuture = null;
  private ExecutorService wrappedExecutorService;
  private File tempDir;
  private File thumbnailsDir;
  private DaoHolder daoHolder;

  @Override
  public File getLoadedThumbnail(final int thumbnailId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints) {
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
    updateAlbumsOnDB();
    Log.i(SERVICE_TAG, pingResponses.toString());
  }

  @Override
  public IBinder onBind(final Intent arg0) {
    return binder;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    executorService = new ScheduledThreadPoolExecutor(2);
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

  }

  @Override
  public void onDestroy() {
    executorService.shutdownNow();
    notificationManager.cancel(NOTIFICATION);
    dnsListener.stopListening();
    stopPolling();
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    if (intent != null) {
      final ServiceCommand command = intent.getParcelableExtra("command");
      if (command != null)
        switch (command) {
        case START:
          Log.i(SERVICE_TAG, "Synchronisation started");
          dnsListener.startListening();
          final Notification notification =
                                            new NotificationCompat.Builder(this).setContentTitle("Syncing")
                                                                                .setSmallIcon(android.R.drawable.ic_dialog_info).getNotification();
          notificationManager.notify(NOTIFICATION, notification);
          startPolling();
          break;
        case STOP:
          dnsListener.stopListening();
          notificationManager.cancel(NOTIFICATION);
          stopPolling();
          break;
        case POLL:
          updateAlbumsOnDB();
          break;
        default:
          break;
        }
    }
    return START_STICKY;
  }

  private <V> V callInTransaction(final Callable<V> callable) {
    return daoHolder.callInTransaction(callable);
  }

  private List<AlbumEntity> findAlbumByArchiveAndName(final ArchiveEntity archiveEntity, final String name) {
    final Map<String, Object> valueMap = new HashMap<String, Object>();
    valueMap.put("archive_id", archiveEntity);
    valueMap.put("name", name);
    return getAlbumDao().queryForFieldValues(valueMap);
  }

  private List<AlbumEntryEntity> findAlbumEntryByAlbumAndName(final AlbumEntity albumEntity, final String name) {
    final Map<String, Object> valueMap = new HashMap<String, Object>();
    valueMap.put("album_id", albumEntity);
    valueMap.put("name", name);
    return getAlbumEntryDao().queryForFieldValues(valueMap);
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

  private String lastPart(final String[] split) {
    if (split == null || split.length == 0)
      return null;
    return split[split.length - 1];
  }

  private URL makeUrl(final InetSocketAddress inetSocketAddress) {
    try {
      return new URL("http", inetSocketAddress.getHostName(), inetSocketAddress.getPort(), "rest");
    } catch (final MalformedURLException e) {
      throw new RuntimeException("Cannot create URL for Socket " + inetSocketAddress, e);
    }
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

  private synchronized void startPolling() {
    pollingFuture = executorService.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        updateAlbumsOnDB();
      }
    }, 10, 20, TimeUnit.MINUTES);

  }

  private synchronized void stopPolling() {
    if (pollingFuture != null)
      pollingFuture.cancel(false);
    pollingFuture = null;
  }

  private synchronized void updateAlbumsOnDB() {
    try {
      final Notification.Builder builder = new Notification.Builder(getApplicationContext());
      builder.setContentTitle("DB Update").setSmallIcon(android.R.drawable.ic_dialog_info).setContentText("Download in progress")
             .setAutoCancel(false);
      notificationManager.notify(UPDATE_DB_NOTIFICATION, NOTIFICATION, builder.getNotification());

      for (final Entry<String, ArchiveConnection> archive : connectionMap.get().entrySet()) {
        final String archiveName = archive.getKey();

        callInTransaction(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = getArchiveDao();
            final ArchiveEntity loadedArchive = archiveDao.queryForId(archiveName);
            if (loadedArchive == null) {
              ArchiveEntity archiveEntity;
              archiveEntity = new ArchiveEntity(archiveName);
              archiveDao.create(archiveEntity);
            }
            return null;
          }
        });
        final Map<String, AlbumConnection> albums = archive.getValue().listAlbums();
        int albumCounter = 0;
        builder.setContentText("Downloading from " + archiveName);
        for (final Entry<String, AlbumConnection> albumEntry : albums.entrySet()) {
          final String albumName = albumEntry.getKey();
          builder.setContentText("Downloading " + lastPart(albumName.split("/")) + " from " + archiveName);
          builder.setProgress(albums.size(), albumCounter++, false);
          notificationManager.notify(UPDATE_DB_NOTIFICATION, NOTIFICATION, builder.getNotification());

          callInTransaction(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
              final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = getArchiveDao();
              final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
              final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();

              final ArchiveEntity archiveEntity = archiveDao.queryForId(archiveName);
              final AlbumDto albumDto = albumEntry.getValue().getAlbumDetail();

              final List<AlbumEntity> foundEntries = findAlbumByArchiveAndName(archiveEntity, albumName);
              final AlbumEntity albumEntity;
              if (foundEntries.isEmpty()) {
                albumEntity = new AlbumEntity(archiveEntity, albumName);
                albumEntity.setAutoAddDate(albumDto.getAutoAddDate());
                albumDao.create(albumEntity);
              } else {
                albumEntity = foundEntries.get(0);
                if ((albumEntity.getAutoAddDate() != null && (albumDto.getAutoAddDate() == null || !albumEntity.getAutoAddDate()
                                                                                                               .equals(albumDto.getAutoAddDate())))
                    || (albumEntity.getAutoAddDate() == null && albumDto.getAutoAddDate() != null)) {
                  albumEntity.setAutoAddDate(albumDto.getAutoAddDate());
                  albumDao.update(albumEntity);
                }
              }

              final Map<String, AlbumEntryEntity> existingEntries = new HashMap<String, AlbumEntryEntity>();
              final Collection<AlbumEntryEntity> entries = albumEntity.getEntries();
              for (final AlbumEntryEntity albumEntryEntity : entries) {
                existingEntries.put(albumEntryEntity.getName(), albumEntryEntity);
              }

              for (final Entry<String, AlbumEntryDto> albumImageEntry : albumDto.getEntries().entrySet()) {
                final AlbumEntryEntity existingEntry = existingEntries.get(albumImageEntry.getKey());
                if (existingEntry == null) {
                  final AlbumEntryEntity albumEntryEntity =
                                                            new AlbumEntryEntity(albumEntity, albumImageEntry.getKey(),
                                                                                 albumImageEntry.getValue().getEntryType());
                  albumEntryDao.create(albumEntryEntity);
                }
              }
              return null;
            }
          });
        }
      }
    } catch (final Throwable t) {
      Log.e(SERVICE_TAG, "Exception while updateing data", t);
    } finally {
      notificationManager.cancel(UPDATE_DB_NOTIFICATION, NOTIFICATION);
    }

  }
}
