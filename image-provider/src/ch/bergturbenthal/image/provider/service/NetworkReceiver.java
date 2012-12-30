package ch.bergturbenthal.image.provider.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcelable;
import android.util.Log;

public class NetworkReceiver extends BroadcastReceiver {
  private static final String NETWORK_RECEIVER_TAG = "NetworkReceiver";

  public static void notifyNetworkState(final Context context) {
    final ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkInfo networkInfo = conn.getActiveNetworkInfo();
    ServiceCommand command;
    if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {
      // start network service
      Log.i(NETWORK_RECEIVER_TAG, "Wifi connected");
      command = ServiceCommand.START;
    } else {
      // stop network service
      Log.i(NETWORK_RECEIVER_TAG, "Wifi disconnected");
      command = ServiceCommand.STOP;
    }
    final Intent intent2 = new Intent(context, SynchronisationServiceImpl.class);
    intent2.putExtra("command", (Parcelable) command);
    final ComponentName componentName = context.startService(intent2);
    Log.i(NETWORK_RECEIVER_TAG, "Service startet: " + componentName);
  }

  @Override
  public void onReceive(final Context context, final Intent intent) {
    notifyNetworkState(context);
  }

}
