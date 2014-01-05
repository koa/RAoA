package ch.bergturbenthal.raoa.provider.service.discovery;

import java.net.InetSocketAddress;
import java.util.Collection;

public interface ServerDiscoveryListener {
	public static final String SERVICE_TYPE = "_images._tcp.";

	public static interface ResultListener {
		void notifyServices(final Collection<InetSocketAddress> knownServiceEndpoints, final boolean withProgressUpdate);
	}

	public abstract void pollForServices(final boolean withProgressUpdate);

	public abstract void startListening();

	public abstract void stopListening();

}