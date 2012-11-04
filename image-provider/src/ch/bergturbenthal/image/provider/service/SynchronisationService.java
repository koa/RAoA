package ch.bergturbenthal.image.provider.service;

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
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;
import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.provider.R;
import ch.bergturbenthal.image.provider.model.AlbumEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryEntity;
import ch.bergturbenthal.image.provider.model.AlbumEntryType;
import ch.bergturbenthal.image.provider.model.ArchiveEntity;
import ch.bergturbenthal.image.provider.orm.DaoHolder;
import ch.bergturbenthal.image.provider.orm.DatabaseHelper;
import ch.bergturbenthal.image.provider.service.MDnsListener.ResultListener;
import ch.bergturbenthal.image.provider.util.ExecutorServiceUtil;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;

public class SynchronisationService extends Service implements ResultListener {

  private final int NOTIFICATION = R.string.synchronisation_service_started;
  private final static String SERVICE_TAG = "Synchronisation Service";

  private NotificationManager notificationManager;
  private MDnsListener dnsListener;
  private ScheduledThreadPoolExecutor executorService;
  private final AtomicReference<Map<String, ArchiveConnection>> connectionMap =
                                                                                new AtomicReference<Map<String, ArchiveConnection>>(
                                                                                                                                    Collections.<String, ArchiveConnection> emptyMap());
  private ExecutorService wrappedExecutorService;

  private final ThreadLocal<DaoHolder> transactionManager = new ThreadLocal<DaoHolder>() {

    @Override
    protected DaoHolder initialValue() {
      return new DaoHolder(connectionSource);
    }
  };
  private ConnectionSource connectionSource;
  private ScheduledFuture<?> pollingFuture = null;

  @Override
  public void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints) {
    final Map<String, Map<URL, PingResponse>> pingResponses = new HashMap<String, Map<URL, PingResponse>>();
    for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
      final URL url = makeUrl(inetSocketAddress);
      final PingResponse response = pingService(url);
      if (response != null) {
        if (pingResponses.containsKey(response.getCollectionId())) {
          pingResponses.get(response.getCollectionId()).put(url, response);
        } else {
          final Map<URL, PingResponse> map = new HashMap<URL, PingResponse>();
          map.put(url, response);
          pingResponses.put(response.getCollectionId(), map);
        }
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
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    executorService = new ScheduledThreadPoolExecutor(2);
    wrappedExecutorService = ExecutorServiceUtil.wrap(executorService);
    connectionSource = DatabaseHelper.makeConnectionSource(getApplicationContext());

    dnsListener = new MDnsListener(getApplicationContext(), this, executorService);
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
    Log.i(SERVICE_TAG, "Synchronisation started " + this);
    final boolean start = intent == null || intent.getBooleanExtra("start", true);
    if (start) {
      dnsListener.startListening();
      final Notification notification =
                                        new NotificationCompat.Builder(this).setContentTitle("Syncing")
                                                                            .setSmallIcon(android.R.drawable.ic_dialog_info).getNotification();
      notificationManager.notify(NOTIFICATION, notification);
      startPolling();
      return START_STICKY;
    } else {
      dnsListener.stopListening();
      notificationManager.cancel(NOTIFICATION);
      stopPolling();
      return START_STICKY;
    }
  }

  private <V> V callInTransaction(final Callable<V> callable) {
    return transactionManager.get().callInTransaction(callable);
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
    return transactionManager.get().getDao(AlbumEntity.class);
  }

  private RuntimeExceptionDao<AlbumEntryEntity, Integer> getAlbumEntryDao() {
    return transactionManager.get().getDao(AlbumEntryEntity.class);
  }

  private RuntimeExceptionDao<ArchiveEntity, String> getArchiveDao() {
    return transactionManager.get().getDao(ArchiveEntity.class);
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

  private void updateAlbumsOnDB() {
    wrappedExecutorService.execute(new Runnable() {

      @Override
      public void run() {
        callInTransaction(new Callable<Void>() {

          @Override
          public Void call() throws Exception {
            final RuntimeExceptionDao<ArchiveEntity, String> archiveDao = getArchiveDao();
            final RuntimeExceptionDao<AlbumEntity, Integer> albumDao = getAlbumDao();
            final RuntimeExceptionDao<AlbumEntryEntity, Integer> albumEntryDao = getAlbumEntryDao();
            for (final Entry<String, ArchiveConnection> archive : connectionMap.get().entrySet()) {
              final String archiveName = archive.getKey();
              final ArchiveEntity loadedArchive = archiveDao.queryForId(archiveName);
              ArchiveEntity archiveEntity;
              if (loadedArchive == null) {
                archiveEntity = new ArchiveEntity(archiveName);
                archiveDao.create(archiveEntity);
              } else
                archiveEntity = loadedArchive;
              final Map<String, AlbumConnection> albums = archive.getValue().listAlbums();
              for (final Entry<String, AlbumConnection> albumEntry : albums.entrySet()) {
                final AlbumConnection albumConnection = albumEntry.getValue();
                final AlbumDetail albumDetail = albumConnection.getAlbumDetail();
                final String name = albumEntry.getKey();
                final List<AlbumEntity> foundEntries = findAlbumByArchiveAndName(archiveEntity, name);
                final AlbumEntity albumEntity;
                if (foundEntries.isEmpty()) {
                  albumEntity = new AlbumEntity(archiveEntity, name);
                  albumEntity.setAutoAddDate(albumDetail.getAutoAddDate());
                  albumDao.create(albumEntity);
                } else {
                  albumEntity = foundEntries.get(0);
                  albumEntity.setAutoAddDate(albumDetail.getAutoAddDate());
                  albumDao.update(albumEntity);
                }
                final Collection<AlbumEntryEntity> entries = albumEntity.getEntries();
                final Map<String, AlbumEntryEntity> existingEntries = new HashMap<String, AlbumEntryEntity>();
                for (final AlbumEntryEntity albumEntryEntity : entries) {
                  existingEntries.put(albumEntryEntity.getName(), albumEntryEntity);
                }
                for (final AlbumImageEntry albumImage : albumDetail.getImages()) {
                  final AlbumEntryEntity existingEntry = existingEntries.get(albumImage.getName());
                  if (existingEntry == null) {
                    final AlbumEntryEntity albumEntryEntity =
                                                              new AlbumEntryEntity(albumEntity, albumImage.getName(),
                                                                                   albumImage.isVideo() ? AlbumEntryType.VIDEO : AlbumEntryType.IMAGE);
                    albumEntryDao.create(albumEntryEntity);
                  }
                }
              }
            }
            return null;
          }

        });
      }
    });
  }
}
