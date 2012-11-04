package ch.bergturbenthal.image.provider.service;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import android.util.Log;
import ch.bergturbenthal.image.data.api.Album;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumList;

public class ServerConnection {
  private static interface ConnectionCallable<V> {
    ResponseEntity<V> call(URL baseUrl) throws Exception;
  }

  private String instanceId;
  private final AtomicReference<Collection<URL>> connections = new AtomicReference<Collection<URL>>(Collections.<URL> emptyList());
  private final RestTemplate restTemplate = new RestTemplate(true);
  private final Album album = new Album() {

    @Override
    public String createAlbum(final String[] pathComps) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public AlbumDetail listAlbumContent(final String albumid) {
      return call(new ConnectionCallable<AlbumDetail>() {

        @Override
        public ResponseEntity<AlbumDetail> call(final URL baseUrl) throws Exception {
          return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums/" + albumid + ".json", AlbumDetail.class);
        }
      });
    }

    @Override
    public AlbumList listAlbums() {
      return call(new ConnectionCallable<AlbumList>() {

        @Override
        public ResponseEntity<AlbumList> call(final URL baseUrl) throws Exception {
          return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums.json", AlbumList.class);
        }
      });
    }

    @Override
    public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void registerClient(final String albumId, final String clientId) {
      // TODO Auto-generated method stub

    }

    @Override
    public void setAutoAddDate(final String albumId, final Date autoAddDate) {
      // TODO Auto-generated method stub

    }

    @Override
    public void unRegisterClient(final String albumId, final String clientId) {
      // TODO Auto-generated method stub

    }
  };

  public AlbumDetail getAlbumDetail(final String albumId) {
    return album.listAlbumContent(albumId);
  }

  public String getInstanceId() {
    return instanceId;
  }

  public Map<String, String> listAlbums() {
    final Map<String, String> ret = new HashMap<String, String>();
    final AlbumList albums = album.listAlbums();
    for (final AlbumEntry entry : albums.getAlbumNames()) {
      ret.put(entry.getName(), entry.getId());
    }
    return ret;
  }

  public void setInstanceId(final String instanceId) {
    this.instanceId = instanceId;
  }

  public void updateServerConnections(final Collection<URL> value) {
    connections.set(value);
  }

  private <V> V call(final ConnectionCallable<V> callable) {
    Throwable t = null;
    for (final URL connection : connections.get()) {
      try {
        final ResponseEntity<V> response;
        response = callable.call(connection);
        if (response.hasBody())
          return response.getBody();
      } catch (final Throwable ex) {
        if (t != null)
          Log.w("Server-connection", "Exception while calling server " + instanceId, t);
        t = ex;
      }
    }
    if (t != null)
      throw new RuntimeException("Cannot connect to server " + instanceId, t);
    else
      throw new RuntimeException("Cannot connect to server " + instanceId + ", no valid connection found");
  }
}
