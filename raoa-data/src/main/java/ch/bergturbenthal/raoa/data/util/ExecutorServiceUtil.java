package ch.bergturbenthal.raoa.data.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExecutorServiceUtil {
  private static final class WrappedExecutorService implements ExecutorService {
    private final ExecutorService service;

    public WrappedExecutorService(final ExecutorService service) {
      this.service = service;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
      return service.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(final Runnable command) {
      service.execute(command);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
      final ArrayList<Future<T>> ret = new ArrayList<Future<T>>();
      for (final Callable<T> task : tasks) {
        ret.add(wrap(service.submit(task)));
      }
      for (final Future<T> future : ret) {
        try {
          future.get();
        } catch (final ExecutionException e) {
        }
      }
      return ret;
    }

    @Override
    public <T> List<Future<T>>
        invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
      return wrapAll(service.invokeAll(tasks, timeout, unit));
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
      return service.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException,
                                                                                                                  ExecutionException,
                                                                                                                  TimeoutException {
      return service.invokeAny(tasks, timeout, unit);
    }

    @Override
    public boolean isShutdown() {
      return service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return service.isTerminated();
    }

    @Override
    public void shutdown() {
      service.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      return service.shutdownNow();
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
      return wrap(service.submit(task));
    }

    @Override
    public Future<?> submit(final Runnable task) {
      return wrap(service.submit(task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
      return wrap(service.submit(task, result));
    }

  }

  private static final class WrappedFuture<V> implements Future<V> {
    public Future<V> innerFuture;

    public WrappedFuture(final Future<V> innerFuture) {
      this.innerFuture = innerFuture;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return innerFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean equals(final Object o) {
      return innerFuture.equals(o);
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      runIfNeeded();
      return innerFuture.get();
    }

    @Override
    public V get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      runIfNeeded();
      return innerFuture.get(timeout, unit);
    }

    @Override
    public int hashCode() {
      return innerFuture.hashCode();
    }

    @Override
    public boolean isCancelled() {
      return innerFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
      return innerFuture.isDone();
    }

    @Override
    public String toString() {
      return innerFuture.toString();
    }

    @SuppressWarnings("rawtypes")
    private void runIfNeeded() {
      final Object innerRef = innerFuture;
      if (innerRef instanceof RunnableFuture)
        ((RunnableFuture) innerRef).run();
    }

  }

  public static ExecutorService wrap(final ExecutorService ex) {
    return new WrappedExecutorService(ex);
  }

  private static <T> Future<T> wrap(final Future<T> future) {
    return new WrappedFuture<T>(future);
  }

  private static <T> ArrayList<Future<T>> wrapAll(final List<Future<T>> futures) {
    final ArrayList<Future<T>> ret = new ArrayList<Future<T>>(futures.size());
    for (final Future<T> future : futures) {
      ret.add(new WrappedFuture<T>(future));
    }
    return ret;
  }
}
