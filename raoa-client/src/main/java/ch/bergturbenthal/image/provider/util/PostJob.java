package ch.bergturbenthal.image.provider.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PostJob {

	private final ExecutorService executorService;
	private final AtomicReference<Runnable> finishRunnable = new AtomicReference<Runnable>(null);
	private final AtomicInteger remainingCount = new AtomicInteger(0);

	public PostJob(final ExecutorService executorService) {
		this.executorService = executorService;
	}

	public <V> Future<V> addTask(final Callable<V> callable) {
		remainingCount.incrementAndGet();
		return executorService.submit(new Callable<V>() {

			@Override
			public V call() throws Exception {
				try {
					return callable.call();
				} finally {
					if (remainingCount.decrementAndGet() == 0) {
						lastFinished();
					}
				}
			}
		});
	}

	public <V> Future<V> finishWith(final Callable<V> callable) {
		final FutureTask<V> finishTask = new FutureTask<V>(callable);
		finishRunnable.set(finishTask);
		if (remainingCount.get() == 0) {
			lastFinished();
		}
		return finishTask;
	}

	protected void lastFinished() {
		final Runnable task = finishRunnable.get();
		if (task != null) {
			executorService.submit(task);
		}
	}
}
