package ch.bergturbenthal.image.client.resolver;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.JmmDNSImpl;

import org.springframework.http.HttpStatus.Series;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import ch.bergturbenthal.image.data.model.PingResponse;

public class Resolver implements Closeable {
  public static interface ConnectionUrlListener {
    void notifyConnectionEstabilshed(String foundUrl);

    void notifyConnectionNotEstablished();
  }

  public static interface ServiceNameListener {
    void nameAdded(String serviceName);

    void nameRemoved(String serviceName);
  }

  private static final String TAG = "Resolver";

  private static final String SERVICE_NAME_URL = "_images._tcp.local.";

  private static final String LAST_SERVICENAME = "lastHostname";

  private static final String LAST_URL = "lastUrl";

  private static String PREFERENCES = "ResolverPreferences";
  private final Context context;
  private final ConcurrentHashMap<InetAddress, JmDNS> interfaces = new ConcurrentHashMap<InetAddress, JmDNS>();

  WifiManager.MulticastLock lock = null;

  private JmmDNS jmmDNS;

  public Resolver(final Context context) {
    this.context = context;
  }

  @Override
  public void close() throws IOException {
    stopRunning();
  }

  public void connectServiceName(final String servicename, final ConnectionUrlListener listener) {
    setup();
    final Callable<Boolean> shutdownRunnable = new Callable<Boolean>() {
      final AtomicBoolean running = new AtomicBoolean(true);

      @Override
      public Boolean call() throws Exception {
        if (running.getAndSet(false)) {
          stopRunning();
          return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }
    };
    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(final Void... params) {
        try {
          Thread.sleep(2 * 1000);
          if (shutdownRunnable.call().booleanValue())
            listener.notifyConnectionNotEstablished();
        } catch (final InterruptedException e) {
        } catch (final Exception e) {
          Log.e(TAG, "Cannot stop mdns", e);
        }
        return null;
      }
    }.execute();
    jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        interfaces.put(event.getInetAddress(), event.getDNS());
        event.getDNS().addServiceListener(SERVICE_NAME_URL, new ServiceListener() {

          @Override
          public void serviceAdded(final ServiceEvent event) {
            if (servicename.equals(event.getName())) {
              event.getDNS().requestServiceInfo(event.getType(), event.getName());
            }
          }

          @Override
          public void serviceRemoved(final ServiceEvent event) {
          }

          @Override
          public void serviceResolved(final ServiceEvent event) {
            if (servicename.equals(event.getName())) {
              notifyAndShutdown(event.getInfo(), listener, shutdownRunnable);
            }
          }
        });
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
        interfaces.remove(event.getInetAddress());
      }
    });
    for (final JmDNS dns : interfaces.values()) {
      final ServiceInfo info = dns.getServiceInfo(SERVICE_NAME_URL, servicename, 20);
      if (info != null) {
        notifyAndShutdown(info, listener, shutdownRunnable);
      }
    }
  }

  public void establishLastConnection(final ConnectionUrlListener listener) {
    final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    final String foundUrl = sharedPreferences.getString(LAST_URL, null);
    if (foundUrl != null) {
      if (pingService(foundUrl)) {
        listener.notifyConnectionEstabilshed(foundUrl);
        return;
      }
    }
    final String lastServiceName = sharedPreferences.getString(LAST_SERVICENAME, null);
    if (lastServiceName != null) {
      connectServiceName(lastServiceName, listener);
    } else
      listener.notifyConnectionNotEstablished();
  }

  public synchronized void findServices(final ServiceNameListener listener) {
    setup();
    jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        interfaces.put(event.getInetAddress(), event.getDNS());
        event.getDNS().addServiceListener(SERVICE_NAME_URL, new ServiceListener() {

          @Override
          public void serviceAdded(final ServiceEvent event) {
            listener.nameAdded(event.getName());
          }

          @Override
          public void serviceRemoved(final ServiceEvent event) {
            listener.nameRemoved(event.getName());
          }

          @Override
          public void serviceResolved(final ServiceEvent event) {
          }
        });
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
        interfaces.remove(event.getInetAddress());
      }
    });
  }

  public synchronized void stopFindingServices() {
    stopRunning();
  }

  private void notifyAndShutdown(final ServiceInfo info, final ConnectionUrlListener listener, final Callable<Boolean> shutdownRunnable) {
    if (!notifyListener(info, listener))
      return;
    stopRunning();
    try {
      shutdownRunnable.call();
    } catch (final Exception e) {
      Log.e(TAG, "Cannot stop mdns", e);
    }
  }

  private boolean notifyListener(final ServiceInfo info, final ConnectionUrlListener listener) {
    for (final String hostname : info.getHostAddresses()) {
      final String url = "http://" + hostname + ":" + info.getPort();
      if (pingService(url)) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        final Editor edit = sharedPreferences.edit();
        edit.putString(LAST_URL, url);
        edit.putString(LAST_SERVICENAME, info.getName());
        edit.commit();
        listener.notifyConnectionEstabilshed(url);
        return true;
      }
    }
    return false;
  }

  private boolean pingService(final String foundUrl) {
    final RestTemplate restTemplate = new RestTemplate();
    try {
      try {
        final ResponseEntity<PingResponse> entity = restTemplate.getForEntity(foundUrl + "/ping.json", PingResponse.class);
        final boolean pingOk = entity.getStatusCode().series() == Series.SUCCESSFUL;
        return pingOk;
      } catch (final ResourceAccessException ex) {
        final Throwable connectException = ex.getCause();
        if (connectException != null && connectException instanceof ConnectException) {
          // try next
          Log.d(TAG, "Connect to " + foundUrl + "/ failed, try more");
          return false;
        } else
          throw ex;
      }
    } catch (final Exception ex) {
      throw new RuntimeException("Cannot connect to " + foundUrl, ex);
    }
  }

  private synchronized void setup() {
    if (jmmDNS == null) {
      if (lock == null) {
        final WifiManager wifi = (WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("ResolverDnsSdLock");
      }
      lock.setReferenceCounted(true);
      lock.acquire();
      jmmDNS = new JmmDNSImpl();
    }
  }

  private synchronized void stopRunning() {
    if (jmmDNS != null) {
      if (lock != null)
        lock.release();
      try {
        jmmDNS.close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
      jmmDNS = null;
      interfaces.clear();
    }
  }
}
