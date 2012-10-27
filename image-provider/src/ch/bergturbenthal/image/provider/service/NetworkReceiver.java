package ch.bergturbenthal.image.provider.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;
import ch.bergturbenthal.image.provider.service.SynchronisationService.LocalBinder;

public class NetworkReceiver extends BroadcastReceiver {
  private static final String NETWORK_RECEIVER_TAG = "NetworkReceiver";
  private final ServiceConnection connection = new ServiceConnection() {

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
      foundServices.put(name, (LocalBinder) service);
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
      foundServices.remove(name);
    }
  };
  private final ConcurrentMap<ComponentName, SynchronisationService.LocalBinder> foundServices =
                                                                                                 new ConcurrentHashMap<ComponentName, SynchronisationService.LocalBinder>();

  @Override
  public void onReceive(final Context context, final Intent intent) {
    final ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkInfo networkInfo = conn.getActiveNetworkInfo();
    final Intent intent2 = new Intent(context, SynchronisationService.class);
    if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
      // start network service
      Log.i(NETWORK_RECEIVER_TAG, "Wifi connected");
      intent2.putExtra("start", true);
    } else {
      // stop network service
      Log.i(NETWORK_RECEIVER_TAG, "Wifi disconnected");
      intent2.putExtra("start", false);
    }
    context.stopService(intent2);
  }

}
