package ch.bergturbenthal.image.client.resolver;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.SelectServerListView;
import ch.bergturbenthal.image.client.resolver.Resolver.ConnectionUrlListener;

/**
 * A adapter for {@link ConnectionUrlListener} starting automatically the
 * {@link SelectServerListView} if connection fails.
 * 
 */
public class ConnectionAdapter implements ConnectionUrlListener {

  private final Context context;
  private boolean connectionStarted = false;
  private final Handler handler;

  private ProgressDialog progressDialog;
  private final ConnectedHandler connectedHandler;

  public ConnectionAdapter(final Context context, final ConnectedHandler connectedHandler) {
    this.context = context;
    this.connectedHandler = connectedHandler;
    handler = new Handler(context.getMainLooper());
  }

  @Override
  public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
    hideProgress();
    connectedHandler.connected(new AlbumService(foundUrl), serverName);
  }

  @Override
  public synchronized void notifyConnectionNotEstablished() {
    hideProgress();
    context.startActivity(new Intent(context, SelectServerListView.class));
  }

  @Override
  public synchronized void notifyStartConnection() {
    if (!connectionStarted) {
      handler.post(new Runnable() {

        @Override
        public void run() {
          progressDialog =
                           ProgressDialog.show(context, "ConnectionAdapter.start",
                                               context.getResources().getString(R.string.wait_for_server_message), true);
        }
      });
    }
    connectionStarted = true;
  }

  @Override
  protected void finalize() throws Throwable {
    hideProgress();
  }

  private void hideProgress() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (progressDialog != null)
          progressDialog.hide();
        progressDialog = null;
      }
    });
  }

}
