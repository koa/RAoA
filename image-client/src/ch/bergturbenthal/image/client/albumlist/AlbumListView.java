package ch.bergturbenthal.image.client.albumlist;

import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.SelectServerListView;
import ch.bergturbenthal.image.client.preferences.Preferences;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.client.resolver.SingleMediaScanner;
import ch.bergturbenthal.image.client.service.DownloadService;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumListView extends ListActivity {
  private AlbumService albumService = null;
  private String clientId;

  public void onAlbumClicked(final View v) {
    final CheckBox checkbox = (CheckBox) v;
    final String albumId = (String) checkbox.getTag();
    if (albumId == null)
      return;
    if (clientId == null)
      return;
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(final Void... params) {
        if (checkbox.isChecked())
          albumService.registerClient(albumId, clientId);
        else
          albumService.unRegisterClient(albumId, clientId);
        return null;
      }
    }.execute();
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
    case R.id.selectServerItem:
      showSelectServerActivity();
      return true;
    case R.id.preferences:
      startActivity(new Intent(getBaseContext(), Preferences.class));
      return true;
    case R.id.start_download:
      startService(new Intent(this, DownloadService.class));
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    clientId = PreferenceManager.getDefaultSharedPreferences(this).getString("client_name", null);
    final AlbumListAdapter albumList = new AlbumListAdapter(this, clientId);
    setListAdapter(albumList);

    final Resolver resolver = new Resolver(this);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        albumService = new AlbumService(foundUrl, getApplicationContext());
        final AlbumList foundAlbums = albumService.listAlbums();
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            albumList.clear();
            for (final AlbumEntry entry : foundAlbums.getAlbumNames()) {
              albumList.add(entry);
            }
          }
        });
      }

      @Override
      public void notifyConnectionNotEstablished() {
        showSelectServerActivity();
      }
    });
    new SingleMediaScanner(this, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
  }

  private void showSelectServerActivity() {
    startActivity(new Intent(AlbumListView.this, SelectServerListView.class));
  }
}
