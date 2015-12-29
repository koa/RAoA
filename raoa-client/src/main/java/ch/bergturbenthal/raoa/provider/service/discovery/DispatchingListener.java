package ch.bergturbenthal.raoa.provider.service.discovery;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import android.content.Context;
import lombok.Builder;

public class DispatchingListener implements ServerDiscoveryListener {
	private final List<ServerDiscoveryListener> listeners;

	@Builder
	public DispatchingListener(final Context context, final ResultListener resultListener, final ScheduledExecutorService executorService) {
		listeners = Arrays.asList(JMDnsListener.builder().context(context).resultListener(resultListener).executorService(executorService).build(),
		                          new FindWellKnownDnsEntriesListener(resultListener));
	}

	@Override
	public void pollForServices(final boolean withProgressUpdate) {
		for (final ServerDiscoveryListener serverDiscoveryListener : listeners) {
			serverDiscoveryListener.pollForServices(withProgressUpdate);
		}
	}

	@Override
	public void startListening() {
		for (final ServerDiscoveryListener serverDiscoveryListener : listeners) {
			serverDiscoveryListener.startListening();
		}
	}

	@Override
	public void stopListening() {
		for (final ServerDiscoveryListener serverDiscoveryListener : listeners) {
			serverDiscoveryListener.stopListening();
		}
	}

}
