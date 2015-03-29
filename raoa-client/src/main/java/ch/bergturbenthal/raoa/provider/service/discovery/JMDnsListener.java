package ch.bergturbenthal.raoa.provider.service.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyEvent;
import javax.jmdns.NetworkTopologyListener;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.JmmDNSImpl;

import lombok.experimental.Builder;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

public class JMDnsListener implements ServerDiscoveryListener {
	private static final String	           MDNS_TAG	        = "jmdns-listener";
	protected static final String	         SERVICE_NAME_URL	= SERVICE_TYPE + "local.";
	private final Context	                 context;
	private final ScheduledExecutorService	executorService;
	private JmmDNS	                       jmmDNS	          = null;
	private MulticastLock	                 lock	            = null;
	private ScheduledFuture<?>	           pendingFuture	  = null;
	private final ResultListener	         resultListener;
	private final Map<InetAddress, JmDNS>	 runningMdns	    = new ConcurrentHashMap<InetAddress, JmDNS>();

	@Builder
	public JMDnsListener(final Context context, final ResultListener resultListener, final ScheduledExecutorService executorService) {
		this.context = context;
		this.resultListener = resultListener;
		this.executorService = executorService;
	}

	@Override
	public synchronized void pollForServices(final boolean withProgressUpdate) {
		if (jmmDNS == null)
			return;
		if (pendingFuture != null) {
			pendingFuture.cancel(false);
		}
		pendingFuture = executorService.schedule(new Runnable() {

			@Override
			public void run() {
				final HashSet<InetSocketAddress> foundEndpoints = new HashSet<InetSocketAddress>();
				synchronized (JMDnsListener.this) {
					for (final Entry<InetAddress, JmDNS> interfaceEntry : runningMdns.entrySet()) {
						// final InetAddress localAddress = interfaceEntry.getKey();
						final JmDNS mdns = interfaceEntry.getValue();
						final ServiceInfo[] serviceInfos = mdns.list(SERVICE_NAME_URL);
						// if (localAddress instanceof Inet6Address && ((Inet6Address) localAddress).getScopeId() != 0) {
						// final int scopedInterface = ((Inet6Address) localAddress).getScopeId();
						// Log.i(MDNS_TAG, "Scoped Interface: " + scopedInterface);
						// for (final ServiceInfo serviceInfo : serviceInfos) {
						// for (final InetAddress hostAddress : serviceInfo.getInetAddresses()) {
						// try {
						// final Inet6Address scopedAddress = Inet6Address.getByAddress(hostAddress.getHostName(), hostAddress.getAddress(), scopedInterface);
						// foundEndpoints.add(new InetSocketAddress(scopedAddress, serviceInfo.getPort()));
						// } catch (final UnknownHostException e) {
						// foundEndpoints.add(new InetSocketAddress(hostAddress, serviceInfo.getPort()));
						// }
						// }
						// }
						// } else {
						for (final ServiceInfo serviceInfo : serviceInfos) {
							for (final InetAddress hostAddress : serviceInfo.getInetAddresses()) {
								foundEndpoints.add(new InetSocketAddress(hostAddress, serviceInfo.getPort()));
							}
						}
						// }
					}
				}
				resultListener.notifyServices(foundEndpoints, withProgressUpdate);
			}
		}, 2, TimeUnit.SECONDS);
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

	@Override
	public synchronized void startListening() {
		setup();
		final ServiceListener listener = new ServiceListener() {

			@Override
			public void serviceAdded(final ServiceEvent event) {
				event.getDNS().requestServiceInfo(event.getType(), event.getName());
			}

			@Override
			public void serviceRemoved(final ServiceEvent event) {
				pollForServices(false);
			}

			@Override
			public void serviceResolved(final ServiceEvent event) {
				pollForServices(true);
			}
		};

		jmmDNS.addServiceListener(SERVICE_NAME_URL, listener);

		jmmDNS.addNetworkTopologyListener(new NetworkTopologyListener() {

			@Override
			public void inetAddressAdded(final NetworkTopologyEvent event) {
				Log.i(MDNS_TAG, "Interface found: " + event.getInetAddress());
				final JmDNS dns = event.getDNS();
				runningMdns.put(event.getInetAddress(), dns);
				dns.addServiceListener(SERVICE_NAME_URL, listener);
				pollForServices(true);
			}

			@Override
			public void inetAddressRemoved(final NetworkTopologyEvent event) {
				runningMdns.remove(event.getInetAddress());
				pollForServices(false);
			}
		});
	}

	@Override
	public synchronized void stopListening() {
		if (jmmDNS != null) {
			if (lock != null) {
				lock.release();
			}
			try {
				jmmDNS.close();
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			jmmDNS = null;
		}
	}
}
