package ch.bergturbenthal.image.server.service;

import java.io.IOException;
import java.net.InetAddress;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceInfo;

import org.springframework.stereotype.Service;

@Service
public class MdnsService {

  private JmmDNS mdns;

  @PostConstruct
  public void startMdnsRegistration() throws IOException {
    final String canonicalHostName = InetAddress.getLocalHost().getHostName();
    mdns = JmmDNS.Factory.getInstance();
    mdns.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        try {
          event.getDNS().registerService(ServiceInfo.create("_images._tcp.local.", "Images-" + canonicalHostName, 10, 10, 8080, "Hello Client"));
        } catch (final IOException e) {
          throw new RuntimeException("Exception while register service", e);
        }
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
        event.getDNS().unregisterAllServices();
      }
    });
    // mdns = JmDNS.create();
    // mdns.registerService(ServiceInfo.create("_images._http._tcp.local.",
    // "server1", 8080, "Hello Client"));
  }

  @PreDestroy
  public void stopMdnsRegistration() {
    mdns.unregisterAllServices();
  }
}
