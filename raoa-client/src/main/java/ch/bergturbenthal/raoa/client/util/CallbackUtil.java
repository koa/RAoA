package ch.bergturbenthal.raoa.client.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class CallbackUtil {
	private static final class CachedCallable<V> implements Callable<V> {
		private final Callable<V>	callable;
		private Exception		      exception	= null;
		private final boolean		  invoked		= false;
		private V		              result;

		private CachedCallable(final Callable<V> callable) {
			this.callable = callable;
		}

		@Override
		public synchronized V call() throws Exception {
			if (invoked) {
				if (exception == null)
					return result;
				else
					throw exception;
			}
			try {
				result = callable.call();
				return result;
			} catch (final Exception ex) {
				exception = ex;
				throw exception;
			}
		}
	}

	public static <V> Callable<V> cache(final Callable<V> callable) {
		if (callable instanceof CachedCallable)
			return callable;
		return new CachedCallable<V>(callable);
	}

	public static <V> List<Callable<V>> callAfterAllFinished(final List<Callable<V>> callables, final Callback<List<CallableResult<V>>> callback) {
		final AtomicInteger remainingResultCount = new AtomicInteger(callables.size());
		final List<Callable<V>> ret = new ArrayList<Callable<V>>(callables.size());
		final List<CallableResult<V>> results = new ArrayList<CallableResult<V>>(callables.size());
		for (int i = 0; i < callables.size(); i++) {
			final int index = i;
			final Callable<V> callable = callables.get(i);
			ret.add(new CallbackCallable<V>(callable, new Callback<V>() {

				private void closeEntry() {
					final int remainingCount = remainingResultCount.decrementAndGet();
					if (remainingCount == 0) {
						callback.complete(results);
					}
				}

				@Override
				public void complete(final V value) {
					results.set(index, CallableResult.valueResult(value));
					closeEntry();
				}

				@Override
				public void exception(final Exception ex) {
					results.set(index, CallableResult.<V> exceptionResult(ex));
					closeEntry();
				}
			}));
		}
		return ret;
	}

	public static <V> Collection<Future<V>> limitConcurrent(final ExecutorService executorService, final int concurrentCount, final Collection<Callable<V>> callables) {
		final LinkedList<Runnable> remainingCallables = new LinkedList<Runnable>();
		final Collection<Future<V>> ret = new ArrayList<Future<V>>(callables.size());
		for (final Callable<V> callable : callables) {
			final FutureTask<V> task = new FutureTask<V>(callable);
			ret.add(task);
			remainingCallables.add(task);
		}
		for (int i = 0; i < concurrentCount && !remainingCallables.isEmpty(); i++) {
			submitNext(executorService, remainingCallables);
		}
		return ret;
	}

	private static <V> void submitNext(final ExecutorService executorService, final Queue<Runnable> remainingCallables) {
		synchronized (remainingCallables) {
			if (remainingCallables.isEmpty())
				return;
			executorService.submit(new CallbackRunnable(new Callback<Void>() {

				@Override
				public void complete(final Void value) {
					submitNext(executorService, remainingCallables);
				}

				@Override
				public void exception(final Exception ex) {
					submitNext(executorService, remainingCallables);
				}
			}, remainingCallables.remove()));
		}
	}
}
