package ch.bergturbenthal.raoa.client.util;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor(suppressConstructorProperties = true)
public class CallbackRunnable implements Runnable {
  @NonNull
  private final Callback<Void> callback;
  @NonNull
  private final Runnable       runnable;

  @Override
  public void run() {

    try {
      runnable.run();
    } catch (final RuntimeException ex) {
      callback.exception(ex);
      throw ex;
    } catch (final Exception ex) {
      callback.exception(ex);
      throw new RuntimeException(ex);
    } catch (final Throwable t) {
      final RuntimeException ex = new RuntimeException(t);
      callback.exception(ex);
      throw ex;
    }
    callback.complete(null);

  }

}
