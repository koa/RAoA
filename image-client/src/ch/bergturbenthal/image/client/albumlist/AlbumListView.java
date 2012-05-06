package ch.bergturbenthal.image.client.albumlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    final Resolver resolver = new Resolver(this);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl, final String serverName) {
        albumService = new AlbumService(foundUrl);
        final AlbumList foundAlbums = albumService.listAlbums();
        final List<AlbumEntry> albums = new ArrayList<AlbumEntry>(foundAlbums.getAlbumNames());
        Collections.sort(albums, new Comparator<AlbumEntry>() {
          @Override
          public int compare(final AlbumEntry o1, final AlbumEntry o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            final AlbumListAdapter albumList = new AlbumListAdapter(AlbumListView.this, clientId, albums);
            setListAdapter(albumList);
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
