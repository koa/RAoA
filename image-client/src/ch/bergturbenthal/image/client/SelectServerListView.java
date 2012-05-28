package ch.bergturbenthal.image.client;

import java.io.IOException;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import ch.bergturbenthal.image.client.resolver.Resolver;

public class SelectServerListView extends ListActivity {

  protected static final String TAG = "MDNS";

  private Resolver resolver;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ArrayAdapter<String> serverList = new ArrayAdapter<String>(this, R.layout.server_list_item);
    setListAdapter(serverList);
    resolver = new Resolver(this);
    resolver.findServices(new Resolver.ServiceNameListener() {

      @Override
      public void nameAdded(final String serviceName) {

        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            serverList.add(serviceName);
          }
        });
      }

      @Override
      public void nameRemoved(final String serviceName) {
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            serverList.remove(serviceName);
          }
        });
      }
    });

    final ListView lv = getListView();
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(final AdapterView<?> arg0, final View arg1, final int item, final long arg3) {
        final String selectedServer = serverList.getItem(item);
        Toast.makeText(getApplicationContext(), R.string.connecting, 2 * 1000).show();
        new AsyncTask<Void, Void, Void>() {

          @Override
          protected Void doInBackground(final Void... params) {
            resolver.connectServiceName(selectedServer, new Resolver.ConnectionUrlListener() {

              @Override
              public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    Toast.makeText(getApplicationContext(), "Connected: " + serverName + "(" + foundUrl + ")", 5000).show();
                  }
                });
                finish();
                // startActivity(new Intent(getApplicationContext(),
                // AlbumListView.class));
              }

              @Override
              public void notifyConnectionNotEstablished() {
                runOnUiThread(new Runnable() {

                  @Override
                  public void run() {
                    Toast.makeText(getApplicationContext(), "Not Connected: ", 5000).show();
                  }
                });
              }
            });
            return null;
          }
        }.execute();
      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    try {
      resolver.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
