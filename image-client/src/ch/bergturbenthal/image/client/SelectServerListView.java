package ch.bergturbenthal.image.client;

import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.app.ListActivity;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class SelectServerListView extends ListActivity {

  protected static final String TAG = "MDNS";

  WifiManager.MulticastLock lock;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ServerListAdapter serverListAdapter = new ServerListAdapter(this);
    setListAdapter(serverListAdapter);
    final ListView lv = getListView();
    // lv.setTextFilterEnabled(true);
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        // When clicked, show a toast with the TextView text
        final ServiceInfo item = (ServiceInfo) serverListAdapter.getItem(position);
        // final String[] hostAddresses = item.getHostAddresses();
        final Intent intent = new Intent(getApplicationContext(), AlbumListView.class);
        intent.putExtra("hostnames", item.getHostAddresses());
        intent.putExtra("port", item.getPort());
        startActivity(intent);
        // Toast.makeText(getApplicationContext(),
        // Arrays.toString(hostAddresses), Toast.LENGTH_SHORT).show();
      }
    });
    setUp();
    try {
      final JmmDNS jmmDNS = JmmDNS.Factory.getInstance();
      final ServiceListener listener = new ServiceListener() {

        @Override
        public void serviceAdded(final ServiceEvent event) {
          // take more data
          event.getDNS().requestServiceInfo(event.getType(), event.getName());
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
          // add to list
          runOnUiThread(new Runnable() {

            @Override
            public void run() {
              serverListAdapter.addInfo(event.getInfo());
            }
          });
        }
      };
      jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

        @Override
        public void inetAddressAdded(final NetworkTopologyEvent event) {
          final JmDNS dns = event.getDNS();
          dns.addServiceListener("_images._tcp.local.", listener);
          // dns.requestServiceInfo("_images._tcp", "Hello");
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
    super.onDestroy();
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
