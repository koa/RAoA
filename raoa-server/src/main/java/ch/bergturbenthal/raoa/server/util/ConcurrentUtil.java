package ch.bergturbenthal.raoa.server.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

public class ConcurrentUtil {
  /**
   * Executes callable not parallel secured by semaphore
   * 
   * @param semaphore
   *          semaphore secure
   * @param wait
   *          true: wait if semaphore is not free, false: return immediately
   * @param callable
   *          callable to execute
   * @return result of callable, null if not executed
   * @throws InterruptedException
   *           wait has interrupted
   */
  public static <V> V executeSequencially(final Semaphore semaphore, final boolean wait, final Callable<V> callable) {
    if (wait)
      try {
        semaphore.acquire();
      } catch (final InterruptedException e1) {
        return null;
      }
    else if (!semaphore.tryAcquire())
      return null;
    try {
      return callable.call();
    } catch (final Throwable e) {
      throw new RuntimeException("Exception in sequencial part", e);
    } finally {
      semaphore.release();
    }
  }
}
