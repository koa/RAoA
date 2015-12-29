package ch.bergturbenthal.raoa.provider.service.discovery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

import lombok.Builder;

public class FindWellKnownDnsEntriesListener implements ServerDiscoveryListener {
	private final ResultListener resultListener;

	@Builder
	public FindWellKnownDnsEntriesListener(final ResultListener resultListener) {
		this.resultListener = resultListener;
	}

	@Override
	public void pollForServices(final boolean withProgressUpdate) {
		try {
			final InetAddress[] inetAddresses = InetAddress.getAllByName("royalarchive.lan");
			final Collection<InetSocketAddress> knownServiceEndpoints = new ArrayList<InetSocketAddress>(inetAddresses.length);
			for (final InetAddress inetAddress : inetAddresses) {
				knownServiceEndpoints.add(new InetSocketAddress(inetAddress, 80));
			}
			resultListener.notifyServices(knownServiceEndpoints, withProgressUpdate);
		} catch (final UnknownHostException e) {
			// ip address not set -> ignore
		}
		// TODO Auto-generated method stub

	}

	@Override
	public void startListening() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stopListening() {
		// TODO Auto-generated method stub

	}

}
