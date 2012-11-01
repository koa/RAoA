package ch.bergturbenthal.image.provider.service;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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
import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.provider.R;
import ch.bergturbenthal.image.provider.service.MDnsListener.ResultListener;

public class SynchronisationService extends Service implements ResultListener {

  private final int NOTIFICATION = R.string.synchronisation_service_started;
  private final static String SERVICE_TAG = "Synchronisation Service";

  private NotificationManager notificationManager;
  private MDnsListener dnsListener;
  private ScheduledThreadPoolExecutor executorService;

  @Override
  public void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints) {
    final Map<InetSocketAddress, PingResponse> pingResponses = new HashMap<InetSocketAddress, PingResponse>();
    for (final InetSocketAddress inetSocketAddress : knownServiceEndpoints) {
      final PingResponse response = pingService(makeUrl(inetSocketAddress));
      if (response != null) {
        pingResponses.put(inetSocketAddress, response);
      }
    }
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
    executorService = new ScheduledThreadPoolExecutor(1);
    dnsListener = new MDnsListener(getApplicationContext(), this, executorService);
  }

  @Override
  public void onDestroy() {
    executorService.shutdownNow();
    notificationManager.cancel(NOTIFICATION);
    dnsListener.stopListening();
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    Log.i(SERVICE_TAG, "Synchronisation started " + this);
    final boolean start = intent.getBooleanExtra("start", true);
    if (start) {
      dnsListener.startListening();
      final Notification notification = new NotificationCompat.Builder(this).setContentTitle("Syncing").getNotification();
      notificationManager.notify(NOTIFICATION, notification);
    } else {
      dnsListener.stopListening();
    }
    return START_STICKY;
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
        return entity.getBody();
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

}
