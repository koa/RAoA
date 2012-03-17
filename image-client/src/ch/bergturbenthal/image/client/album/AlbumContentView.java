package ch.bergturbenthal.image.client.album;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.AdapterView;
import ch.bergturbenthal.image.client.R;
import ch.bergturbenthal.image.client.SelectServerListView;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.client.resolver.Resolver;
import ch.bergturbenthal.image.data.model.AlbumDetail;

public class AlbumContentView extends Activity {
  private AlbumService service;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final String albumId = getIntent().getStringExtra("albumId");
    setContentView(R.layout.album_content_list);

    final AlbumThumbnailAdapter albumThumbnailAdapter = new AlbumThumbnailAdapter(this, albumId);
    final AdapterView<AlbumThumbnailAdapter> gridView = (AdapterView<AlbumThumbnailAdapter>) findViewById(R.id.gridview);
    gridView.setAdapter(albumThumbnailAdapter);
    final Resolver resolver = new Resolver(this);
    resolver.establishLastConnection(new Resolver.ConnectionUrlListener() {

      @Override
      public void notifyConnectionEstabilshed(final String foundUrl) {
        service = new AlbumService(foundUrl, AlbumContentView.this);
        final AlbumDetail albumContent = service.listAlbumContent(albumId);
        runOnUiThread(new Runnable() {

          @Override
          public void run() {
            albumThumbnailAdapter.setService(service);
            albumThumbnailAdapter.setImages(albumContent.getImages(), AlbumContentView.this);
          }
        });
      }

      @Override
      public void notifyConnectionNotEstablished() {
        showSelectServerActivity();
      }
    });
  }

  private void showSelectServerActivity() {
    startActivity(new Intent(this, SelectServerListView.class));
  }

}
