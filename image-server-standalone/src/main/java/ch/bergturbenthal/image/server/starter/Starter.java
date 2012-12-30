package ch.bergturbenthal.image.server.starter;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceInfo;

import org.apache.jasper.servlet.JspServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.DispatcherServlet;

public class Starter {
  private static final Logger logger = LoggerFactory.getLogger(Starter.class);

  /**
   * @param args
   * @throws Exception
   */
  public static void main(final String[] args) throws Exception {
    // TODO Auto-generated method stub

    // final URL warUrl = new ClassPathResource("image-server.war").getURL();
    final Server server = new Server();
    final ServerConnector connector = new ServerConnector(server);
    if (args.length == 1)
      connector.setPort(Integer.parseInt(args[0]));
    server.setConnectors(new Connector[] { connector });
    final ServletContextHandler context = new ServletContextHandler();
    context.setSessionHandler(new SessionHandler());
    context.addServlet(JspServlet.class, "*.jsp");
    context.setClassLoader(ClassLoader.getSystemClassLoader());
    final SpringResource baseResource = new SpringResource(new ClassPathResource("web/"));
    context.setBaseResource(baseResource);
    final ServletHolder dispatcherServlet = context.addServlet(DispatcherServlet.class, "/rest/*");
    dispatcherServlet.setInitParameter("contextConfigLocation", "classpath:/spring/servlet-context.xml");
    dispatcherServlet.setInitOrder(1);

    final HandlerCollection handlers = new HandlerCollection();
    final ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setBaseResource(baseResource);
    handlers.setHandlers(new Handler[] { context, resourceHandler, new DefaultHandler() });
    server.setHandler(handlers);
    server.start();
    final int localPort = connector.getLocalPort();

    final JmmDNS jmmsImpl = JmmDNS.Factory.getInstance();
    jmmsImpl.addNetworkTopologyListener(new NetworkTopologyListener() {

      @Override
      public void inetAddressAdded(final NetworkTopologyEvent event) {
        try {
          final InetAddress inetAddress = event.getInetAddress();
          if (!inetAddress.isLinkLocalAddress())
            event.getDNS().registerService(ServiceInfo.create("_images._tcp.local", "Standalone", localPort, ""));
        } catch (final IOException e) {
          logger.error("Cannot register service", e);
        }
      }

      @Override
      public void inetAddressRemoved(final NetworkTopologyEvent event) {
      }
    });

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          jmmsImpl.close();
        } catch (final IOException e) {
          logger.warn("Cannot shutdown mDNS", e);
        }
      }
    }));

    server.join();
  }
}
