package ch.bergturbenthal.image.client.resolver;

import java.io.File;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import ch.bergturbenthal.image.data.api.Album;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumService implements Album {
  private final RestTemplate restTemplate;
  private final String baseUrl;

  public AlbumService(final String baseUrl) {
    this.baseUrl = baseUrl;
    restTemplate = new RestTemplate();
  }

  @Override
  public AlbumDetail listAlbumContent(final String albumid) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AlbumList listAlbums() {
    final ResponseEntity<AlbumList> response = restTemplate.getForEntity(baseUrl + "/albums.json", AlbumList.class);
    if (response.hasBody())
      return response.getBody();
    throw new RuntimeException("Response without body while calling " + baseUrl + " status: " + response.getStatusCode());
  }

  @Override
  public File readImage(final String albumId, final String imageId, final int width, final int height) {
    // TODO Auto-generated method stub
    return null;
  }

}
