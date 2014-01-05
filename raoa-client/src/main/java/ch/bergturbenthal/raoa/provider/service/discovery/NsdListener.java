package ch.bergturbenthal.raoa.provider.service.discovery;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.experimental.Builder;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public class NsdListener implements ServerDiscoveryListener {
	private static final String TAG = "nsd-discovery-listener";
	private final ConcurrentMap<String, Collection<InetSocketAddress>> knownSockets = new ConcurrentHashMap<String, Collection<InetSocketAddress>>();
	private final DiscoveryListener listener = new DiscoveryListener() {

		// Called as soon as service discovery begins.
		@Override
		public void onDiscoveryStarted(final String regType) {
			Log.d(TAG, "Service discovery started");
		}

		@Override
		public void onDiscoveryStopped(final String serviceType) {
			Log.i(TAG, "Discovery stopped: " + serviceType);
		}

		@Override
		public void onServiceFound(final NsdServiceInfo service) {
			// A service was found! Do something with it.
			Log.d(TAG, "Service discovery success " + service);
			if (!service.getServiceType().equals(SERVICE_TYPE)) {
				// Service type is the string containing the protocol and
				// transport layer for this service.
				Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
			} else {
				final String serviceName = service.getServiceName();
				knownSockets.putIfAbsent(serviceName, new ArrayList<InetSocketAddress>());
				final Collection<InetSocketAddress> serviceAddresses = knownSockets.get(serviceName);
				nsdManager.resolveService(service, new ResolveListener() {

					@Override
					public void onResolveFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
						Log.w(TAG, " Resolve failed for " + serviceName + ": " + serviceInfo + ", Error-Code: " + errorCode);
					}

					@Override
					public void onServiceResolved(final NsdServiceInfo serviceInfo) {
						synchronized (serviceAddresses) {
							serviceAddresses.add(new InetSocketAddress(serviceInfo.getHost(), serviceInfo.getPort()));
						}
						notifyKnownEndpoints();
					}
				});
			}
		}

		@Override
		public void onServiceLost(final NsdServiceInfo service) {
			// When the network service is no longer available.
			// Internal bookkeeping code goes here.
			Log.e(TAG, "service lost" + service);
			if (!service.getServiceType().equals(SERVICE_TYPE)) {
				return;
			}
			final Collection<InetSocketAddress> addresses = knownSockets.get(service.getServiceName());
			if (addresses != null) {
				synchronized (addresses) {
					addresses.clear();
				}
			}
			notifyKnownEndpoints();
		}

		@Override
		public void onStartDiscoveryFailed(final String serviceType, final int errorCode) {
			Log.e(TAG, "Discovery failed: Error code:" + errorCode);
			nsdManager.stopServiceDiscovery(this);
		}

		@Override
		public void onStopDiscoveryFailed(final String serviceType, final int errorCode) {
			Log.e(TAG, "Discovery failed: Error code:" + errorCode);
			nsdManager.stopServiceDiscovery(this);
		}
	};

	private final NsdManager nsdManager;

	private final ResultListener resultListener;

	@Builder
	public NsdListener(final Context context, final ResultListener resultListener) {
		this.resultListener = resultListener;
		nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
	}

	private Collection<InetSocketAddress> collectVisibleEndpoints() {
		final ArrayList<InetSocketAddress> ret = new ArrayList<InetSocketAddress>();
		for (final Entry<String, Collection<InetSocketAddress>> serviceEntry : knownSockets.entrySet()) {
			final Collection<InetSocketAddress> connections = serviceEntry.getValue();
			synchronized (connections) {
				ret.addAll(connections);
			}
		}
		return ret;
	}

	protected void notifyKnownEndpoints() {
		pollForServices(true);
	}

	@Override
	public void pollForServices(final boolean withProgressUpdate) {
		final Collection<InetSocketAddress> visibleEndpoints = collectVisibleEndpoints();
		resultListener.notifyServices(visibleEndpoints, withProgressUpdate);
		// Log.i(TAG, "Known Services: " + knownSockets);

		// TODO Auto-generated method stub

	}

	@Override
	public void startListening() {
		nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);

	}

	@Override
	public void stopListening() {
		if (nsdManager != null) {
			nsdManager.stopServiceDiscovery(listener);
		}
	}

}
