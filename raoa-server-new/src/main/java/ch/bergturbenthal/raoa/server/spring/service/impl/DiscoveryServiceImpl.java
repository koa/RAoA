package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.PostConstruct;
import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.DiscoveryService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DiscoveryServiceImpl implements DiscoveryService {
	private final JmmDNS jmmdns = JmmDNS.Factory.getInstance();
	@Autowired
	private ServerProperties serverProperties;
	@Autowired
	private ServletContext servletContext;

	@PostConstruct
	public void init() throws IOException {
		final String contextPath = servletContext.getContextPath() == null ? "/" : servletContext.getContextPath();
		final int port = serverProperties.getPort() == null ? 8080 : serverProperties.getPort();
		final ServiceInfo info = ServiceInfo.create("http", "RAoA", "raoa", port, 1, 1, Collections.singletonMap("path", contextPath));
		jmmdns.registerService(info);
		jmmdns.addServiceListener("_http._tcp.local.", new ServiceListener() {

			@Override
			public void serviceAdded(final ServiceEvent event) {
			}

			@Override
			public void serviceRemoved(final ServiceEvent event) {
				// TODO Auto-generated method stub

			}

			@Override
			public void serviceResolved(final ServiceEvent event) {
				final ServiceInfo info2 = event.getInfo();
				if (info2.getSubtype().equals("raoa")) {
					// log.info("Resolved " + info2.getName() + ":" + info2.getSubtype() + "
					// -----------------------------------------------------------------------------");
					// for (final InetAddress address : info2.getInetAddresses()) {
					// if (address instanceof Inet6Address && address.isLinkLocalAddress()) {
					// final NetworkInterface scopedInterface = ((Inet6Address) address).getScopedInterface();
					// if (scopedInterface == null) {
					// log.info("no scoped interface: " + address);
					// // continue;
					// } else {
					// log.info("Link-Local address found: " + address.getCanonicalHostName() + "%" + scopedInterface.getName());
					// }
					// } else {
					// log.info("Address found: " + address.getCanonicalHostName());
					// }
					// }
					for (final String url : info2.getURLs()) {
						log.info("Service found: " + url);
					}
				}
			}
		});
	}
}
