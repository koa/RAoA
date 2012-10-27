package ch.bergturbenthal.image.provider.service;

import android.app.Activity;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;

public class NetworkActivity extends Activity {

  private NetworkReceiver receiver;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Registers BroadcastReceiver to track network connection changes.
    final IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    receiver = new NetworkReceiver();
    this.registerReceiver(receiver, filter);
  }

  @Override
  protected void onDestroy() {
    // TODO Auto-generated method stub
    super.onDestroy();
    // Unregisters BroadcastReceiver when app is destroyed.
    if (receiver != null) {
      this.unregisterReceiver(receiver);
    }
  }

  @Override
  protected void onStart() {
    // TODO Auto-generated method stub
    super.onStart();
  }

}
