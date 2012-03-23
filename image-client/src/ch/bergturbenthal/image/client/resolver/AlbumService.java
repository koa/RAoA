package ch.bergturbenthal.image.client.resolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import android.content.Context;
import ch.bergturbenthal.image.data.api.Album;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumService implements Album {
  private final String baseUrl;
  private final Context context;
  private final RestTemplate restTemplate;
  private final ConcurrentMap<File, Object> waitMap = new ConcurrentHashMap<File, Object>();

  public AlbumService(final String baseUrl, final Context context) {
    this.baseUrl = baseUrl;
    this.context = context;
    restTemplate = new RestTemplate();
  }

  @Override
  public AlbumDetail listAlbumContent(final String albumid) {
    final ResponseEntity<AlbumDetail> response = restTemplate.getForEntity(baseUrl + "/albums/{id}.json", AlbumDetail.class, albumid);
    if (response.hasBody())
      return response.getBody();
    throw new RuntimeException("Response without body while calling " + baseUrl + " status: " + response.getStatusCode());
  }

  @Override
  public AlbumList listAlbums() {
    final ResponseEntity<AlbumList> response = restTemplate.getForEntity(baseUrl + "/albums.json", AlbumList.class);
    if (response.hasBody())
      return response.getBody();
    throw new RuntimeException("Response without body while calling " + baseUrl + " status: " + response.getStatusCode());
  }

  @Override
  public ImageResult readImage(final String albumId, final String imageId, final int width, final int height, final Date ifModifiedSince) {
    return restTemplate.execute(baseUrl + "/albums/{albumId}/image/{imageId}-{width}x{height}.jpg", HttpMethod.GET, new RequestCallback() {
      @Override
      public void doWithRequest(final ClientHttpRequest request) throws IOException {
        if (ifModifiedSince != null)
          request.getHeaders().setIfModifiedSince(ifModifiedSince.getTime());
      }
    }, new ResponseExtractor<ImageResult>() {
      @Override
      public ImageResult extractData(final ClientHttpResponse response) throws IOException {
        if (response.getStatusCode() == HttpStatus.NOT_MODIFIED)
          return new ImageResult(null, null);
        final long lastModified = response.getHeaders().getLastModified();
        final Date lastModifiedDate;
        if (lastModified > 0)
          lastModifiedDate = new Date(lastModified);
        else
          lastModifiedDate = null;
        final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        final InputStream inputStream = response.getBody();
        final byte[] buffer = new byte[8192];
        while (true) {
          final int read = inputStream.read(buffer);
          if (read < 0)
            break;
          arrayOutputStream.write(buffer, 0, read);
        }
        return new ImageResult(lastModifiedDate, new ImageResult.StreamSource() {
          @Override
          public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(arrayOutputStream.toByteArray());
          }
        });
      }
    }, albumId, imageId, width, height);

  }

  @Override
  public void registerClient(final String albumId, final String clientId) {
    restTemplate.put(baseUrl + "/albums/{albumId}/registerClient", clientId, albumId);
  }

  @Override
  public void unRegisterClient(final String albumId, final String clientId) {
    restTemplate.put(baseUrl + "/albums/{albumId}/unRegisterClient", clientId, albumId);
  }

}
