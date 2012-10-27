package ch.bergturbenthal.image.provider.service;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jmdns.JmDNS;
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
import android.util.Log;

public class MDnsListener {
  private JmmDNS jmmDNS = null;
  private MulticastLock lock = null;
  private final Context context;
  private final ConcurrentHashMap<InetAddress, JmDNS> interfaces = new ConcurrentHashMap<InetAddress, JmDNS>();
  private static final String SERVICE_NAME_URL = "_images._tcp.local.";
  private static final String MDNS_TAG = "mdns-listener";

  public MDnsListener(final Context context) {
    this.context = context;
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

  public synchronized void startListening() {
    setup();
    final ConcurrentMap<String, AtomicInteger> visibleServices = new ConcurrentHashMap<String, AtomicInteger>();
    jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        interfaces.put(event.getInetAddress(), event.getDNS());
        event.getDNS().addServiceListener(SERVICE_NAME_URL, new ServiceListener() {

          @Override
          public void serviceAdded(final ServiceEvent event) {
            event.getDNS().requestServiceInfo(event.getType(), event.getName());
          }

          @Override
          public void serviceRemoved(final ServiceEvent event) {
            final String name = event.getName();
            Log.i(MDNS_TAG, "Service " + name + " removed");
            visibleServices.putIfAbsent(name, new AtomicInteger(0));
            final int newCountAfter = visibleServices.get(name).decrementAndGet();
            if (newCountAfter < 0) {
              visibleServices.get(name).compareAndSet(newCountAfter, 0);
            }
          }

          @Override
          public void serviceResolved(final ServiceEvent event) {
            final String name = event.getName();
            Log.i(MDNS_TAG, "Service " + name + " found");
            final ServiceInfo serviceInfo = event.getInfo();
            Log.i(MDNS_TAG, "Serviceinfo: " + serviceInfo);
            Log.i(MDNS_TAG, "Addresses: " + Arrays.toString(serviceInfo.getInetAddresses()));
            visibleServices.putIfAbsent(name, new AtomicInteger(0));
            final int newCountAfter = visibleServices.get(name).incrementAndGet();
            if (newCountAfter == 1) {
              // listener.nameAdded(name);
            }
          }
        });
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
        interfaces.remove(event.getInetAddress());
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
      interfaces.clear();
    }
  }
}
