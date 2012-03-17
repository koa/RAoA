package ch.bergturbenthal.image.client;

import java.util.Comparator;
import java.util.Date;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumListView extends ListActivity {
  private AlbumService albumService = null;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final AlbumListAdapter albumList = new AlbumListAdapter(this);
    setListAdapter(albumList);
    final Resolver resolver = new Resolver(this);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl) {
        albumService = new AlbumService(foundUrl);
        final AlbumList foundAlbums = albumService.listAlbums();
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            albumList.clear();
            for (final AlbumEntry entry : foundAlbums.getAlbumNames()) {
              albumList.add(entry);
            }
            albumList.sort(new Comparator<AlbumEntry>() {

              @Override
              public int compare(final AlbumEntry lhs, final AlbumEntry rhs) {
                final Date firstPhotoDate1 = lhs.getFirstPhotoDate();
                final Date firstPhotoDate2 = rhs.getFirstPhotoDate();
                if (firstPhotoDate1 == null) {
                  if (firstPhotoDate2 == null)
                    return 0;
                  return 1;
                }
                if (firstPhotoDate2 == null)
                  return -1;
                return firstPhotoDate1.compareTo(firstPhotoDate2);
              }
            });
          }
        });
      }

      @Override
      public void notifyConnectionNotEstablished() {
        startActivity(new Intent(AlbumListView.this, SelectServerListView.class));
      }
    });
  }

}
