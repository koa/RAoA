package ch.bergturbenthal.image.client;

import java.io.IOException;

import android.app.ListActivity;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import ch.bergturbenthal.image.client.resolver.Resolver;

public class SelectServerListView extends ListActivity {

  protected static final String TAG = "MDNS";

  WifiManager.MulticastLock lock;

  private Resolver resolver;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ArrayAdapter<String> serverList = new ArrayAdapter<String>(this, R.layout.list_item);
    setListAdapter(serverList);
    resolver = new Resolver(this);
    resolver.findServices(new Resolver.ServiceNameListener() {

      @Override
      public void nameAdded(final String serviceName) {

        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            serverList.add(serviceName);
          }
        });
      }

      @Override
      public void nameRemoved(final String serviceName) {
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            serverList.remove(serviceName);
          }
        });
      }
    });

    final ListView lv = getListView();
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(final AdapterView<?> arg0, final View arg1, final int item, final long arg3) {
        final String selectedServer = serverList.getItem(item);
        Toast.makeText(getApplicationContext(), R.string.connecting, 2 * 1000).show();
        new AsyncTask<Void, Void, Void>() {

          @Override
          protected Void doInBackground(final Void... params) {
            resolver.connectServiceName(selectedServer, new Resolver.ConnectionUrlListener() {

              @Override
              public void notifyConnectionEstabilshed(final String foundUrl) {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    Toast.makeText(getApplicationContext(), "Connected: " + foundUrl, 5000).show();
                  }
                });
                startActivity(new Intent(getApplicationContext(), AlbumListView.class));
              }

              @Override
              public void notifyConnectionNotEstablished() {
                runOnUiThread(new Runnable() {

                  @Override
                  public void run() {
                    Toast.makeText(getApplicationContext(), "Not Connected: ", 5000).show();
                  }
                });
              }
            });
            return null;
          }
        }.execute();
      }
    });
    // // lv.setTextFilterEnabled(true);
    // lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
    //
    // @Override
    // public void onItemClick(final AdapterView<?> parent, final View view,
    // final int position, final long id) {
    // // When clicked, show a toast with the TextView text
    // final ServiceInfo item = (ServiceInfo)
    // serverListAdapter.getItem(position);
    // // final String[] hostAddresses = item.getHostAddresses();
    // final Intent intent = new Intent(getApplicationContext(),
    // AlbumListView.class);
    // intent.putExtra("hostnames", item.getHostAddresses());
    // intent.putExtra("port", item.getPort());
    // startActivity(intent);
    // // Toast.makeText(getApplicationContext(),
    // // Arrays.toString(hostAddresses), Toast.LENGTH_SHORT).show();
    // }
    // });
    // setUp();
    // try {
    // final JmmDNS jmmDNS = JmmDNS.Factory.getInstance();
    // final ServiceListener listener = new ServiceListener() {
    //
    // @Override
    // public void serviceAdded(final ServiceEvent event) {
    // // take more data
    // event.getDNS().requestServiceInfo(event.getType(), event.getName());
    // }
    //
    // @Override
    // public void serviceRemoved(final ServiceEvent event) {
    // runOnUiThread(new Runnable() {
    //
    // @Override
    // public void run() {
    // serverListAdapter.removeInfo(event.getInfo());
    // }
    // });
    // }
    //
    // @Override
    // public void serviceResolved(final ServiceEvent event) {
    // // add to list
    // runOnUiThread(new Runnable() {
    //
    // @Override
    // public void run() {
    // serverListAdapter.addInfo(event.getInfo());
    // }
    // });
    // }
    // };
    // jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {
    //
    // @Override
    // public void inetAddressAdded(final NetworkTopologyEvent event) {
    // final JmDNS dns = event.getDNS();
    // dns.addServiceListener("_images._tcp.local.", listener);
    // // dns.requestServiceInfo("_images._tcp", "Hello");
    // Log.i(TAG, "new Address: " + event);
    // }
    //
    // @Override
    // public void inetAddressRemoved(final NetworkTopologyEvent event) {
    // event.getDNS().unregisterAllServices();
    // Log.i(TAG, "removed Address: " + event);
    // }
    // });
    // Log.i(TAG, "Initialization called");
    // } catch (final Exception e) {
    // throw new RuntimeException("Cannot initialize mdns", e);
    // }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    try {
      resolver.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    // if (lock != null)
    // lock.release();
  }

  private void setUp() { // to be called by onCreate
    final WifiManager wifi = (WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
    lock = wifi.createMulticastLock("HeeereDnssdLock");
    lock.setReferenceCounted(true);
    lock.acquire();
  }
}
