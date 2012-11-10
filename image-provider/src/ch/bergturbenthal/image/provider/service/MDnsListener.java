package ch.bergturbenthal.image.provider.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.JmmDNSImpl;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;

public class MDnsListener {
  public static interface ResultListener {
    void notifyServices(Collection<InetSocketAddress> knownServiceEndpoints);
  }

  private JmmDNS jmmDNS = null;
  private MulticastLock lock = null;
  private final Context context;
  private static final String SERVICE_NAME_URL = "_images._tcp.local.";
  private static final String MDNS_TAG = "mdns-listener";
  private final ScheduledExecutorService executorService;
  private ScheduledFuture<?> pendingFuture = null;
  private final ResultListener resultListener;

  public MDnsListener(final Context context, final ResultListener resultListener, final ScheduledExecutorService executorService) {
    this.context = context;
    this.resultListener = resultListener;
    this.executorService = executorService;
  }

  public synchronized void pollForServices() {
    if (jmmDNS == null)
      return;
    if (pendingFuture != null) {
      pendingFuture.cancel(false);
    }
    pendingFuture = executorService.schedule(new Runnable() {

      @Override
      public void run() {
        synchronized (MDnsListener.this) {
          final HashSet<InetSocketAddress> foundEndpoints = new HashSet<InetSocketAddress>();
          final ServiceInfo[] serviceInfos = jmmDNS.list(SERVICE_NAME_URL, 1500);
          for (final ServiceInfo serviceInfo : serviceInfos) {
            for (final InetAddress hostAddress : serviceInfo.getInetAddresses()) {
              foundEndpoints.add(new InetSocketAddress(hostAddress, serviceInfo.getPort()));
            }
          }
          resultListener.notifyServices(foundEndpoints);
        }
      }
    }, 2, TimeUnit.SECONDS);
  }

  public synchronized void startListening() {
    setup();
    final ServiceListener listener = new ServiceListener() {

      @Override
      public void serviceAdded(final ServiceEvent event) {
        event.getDNS().requestServiceInfo(event.getType(), event.getName());
      }

      @Override
      public void serviceRemoved(final ServiceEvent event) {
        pollForServices();
      }

      @Override
      public void serviceResolved(final ServiceEvent event) {
        pollForServices();
      }
    };

    jmmDNS.addServiceListener(SERVICE_NAME_URL, listener);

    jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        jmmDNS.addServiceListener(SERVICE_NAME_URL, listener);
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
        pollForServices();
      }
    });
  }

  public synchronized void stopListening() {
    if (jmmDNS != null) {
      if (lock != null)
        lock.release();
      try {
        jmmDNS.close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
      jmmDNS = null;
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
}
