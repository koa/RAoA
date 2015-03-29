package ch.bergturbenthal.raoa.client.util;

import java.util.concurrent.Callable;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor(suppressConstructorProperties = true)
public class CallbackCallable<V> implements Callable<V> {

	@NonNull
	private final Callable<V>	callable;
	@NonNull
	private final Callback<V>	callback;

	@Override
	public V call() throws Exception {
		final V ret;
		try {
			ret = callable.call();
		} catch (final Exception ex) {
			callback.exception(ex);
			throw ex;
		} catch (final Throwable t) {
			final Exception ex = new Exception(t);
			callback.exception(ex);
			throw ex;
		}
		callback.complete(ret);
		return ret;
	}
}
