package ch.bergturbenthal.image.client;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import android.app.ListActivity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

public class SelectServerListView extends ListActivity {

  protected static final String TAG = "MDNS";

  WifiManager.MulticastLock lock;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ServerListAdapter serverListAdapter = new ServerListAdapter(this);
    setListAdapter(serverListAdapter);
    setUp();
    try {
      final JmmDNS jmmDNS = JmmDNS.Factory.getInstance();
      final ServiceListener listener = new ServiceListener() {

        @Override
        public void serviceAdded(final ServiceEvent event) {
        }

        @Override
        public void serviceRemoved(final ServiceEvent event) {
          runOnUiThread(new Runnable() {

            @Override
            public void run() {
              serverListAdapter.removeInfo(event.getInfo());
            }
          });
        }

        @Override
        public void serviceResolved(final ServiceEvent event) {
          runOnUiThread(new Runnable() {

            @Override
            public void run() {
              serverListAdapter.addInfo(event.getInfo());
            }
          });
        }
      };
      // jmmDNS.addServiceListener("_images._http._tcp.local", listener);
      jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

        @Override
        public void inetAddressAdded(final NetworkTopologyEvent event) {
          event.getDNS().addServiceListener("_images._tcp.local.", listener);
          Log.i(TAG, "new Address: " + event);
        }

        @Override
        public void inetAddressRemoved(final NetworkTopologyEvent event) {
          event.getDNS().unregisterAllServices();
          Log.i(TAG, "removed Address: " + event);
        }
      });
      Log.i(TAG, "Initialization called");
    } catch (final Exception e) {
      throw new RuntimeException("Cannot initialize mdns", e);
    }
  }

  @Override
  protected void onDestroy() {
    if (lock != null)
      lock.release();
  }

  private void setUp() { // to be called by onCreate
    final WifiManager wifi = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
    lock = wifi.createMulticastLock("HeeereDnssdLock");
    lock.setReferenceCounted(true);
    lock.acquire();
  }
}
