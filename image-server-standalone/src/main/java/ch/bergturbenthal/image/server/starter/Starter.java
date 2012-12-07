package ch.bergturbenthal.image.server.starter;

import java.io.IOException;
import java.net.URL;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceInfo;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class Starter {
  private static final Logger logger = LoggerFactory.getLogger(Starter.class);

  /**
   * @param args
   * @throws Exception
   */
  public static void main(final String[] args) throws Exception {
    // TODO Auto-generated method stub

    final URL warUrl = new ClassPathResource("image-server.war").getURL();
    final Server server = new Server();
    final ServerConnector connector = new ServerConnector(server);
    // connector.setPort(8080);
    server.setConnectors(new Connector[] { connector });
    final ServletContextHandler context = new WebAppContext(warUrl.toExternalForm(), "/");

    final HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] { context, new DefaultHandler() });
    server.setHandler(handlers);
    server.start();
    final int localPort = connector.getLocalPort();

    JmmDNS.Factory.getInstance().addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        try {
          event.getDNS().registerService(ServiceInfo.create("_images._tcp.local", "Standalone", localPort, ""));
        } catch (final IOException e) {
          logger.error("Cannot register service", e);
        }
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
      }
    });

    server.join();
  }

}
