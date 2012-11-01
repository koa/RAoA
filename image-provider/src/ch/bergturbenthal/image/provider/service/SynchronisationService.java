package ch.bergturbenthal.image.provider.service;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
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
    // TODO Auto-generated method stub

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

}
