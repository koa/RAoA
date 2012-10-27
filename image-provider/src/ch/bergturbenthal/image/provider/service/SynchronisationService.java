package ch.bergturbenthal.image.provider.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import ch.bergturbenthal.image.provider.R;

public class SynchronisationService extends Service {
  /**
   * Class for clients to access. Because we know this service always runs in
   * the same process as its clients, we don't need to deal with IPC.
   */
  public class LocalBinder extends Binder {
    SynchronisationService getService() {
      return SynchronisationService.this;
    }
  }

  private final int NOTIFICATION = R.string.synchronisation_service_started;
  private final static String SERVICE_TAG = "Synchronisation Service";

  private final IBinder binder = new LocalBinder();
  private NotificationManager notificationManager;
  private MDnsListener dnsListener;

  @Override
  public IBinder onBind(final Intent arg0) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    dnsListener = new MDnsListener(getApplicationContext());
  }

  @Override
  public void onDestroy() {
    notificationManager.cancel(NOTIFICATION);
    dnsListener.stopListening();
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    Log.i(SERVICE_TAG, "Synchronisation started");
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
