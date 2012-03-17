package ch.bergturbenthal.image.client.resolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
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
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumService implements Album {
  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final Context context;
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
  public File readImage(final String albumId, final String imageId, final int width, final int height) {
    final String filename = MessageFormat.format("image-{0}-{1}-{2}x{3}.jpg", albumId, imageId, width, height);
    final File cachedFile = new File(context.getCacheDir(), filename);
    try {
      while (waitMap.putIfAbsent(cachedFile, this) != null) {
        try {
          Thread.sleep(50);
        } catch (final InterruptedException e) {
        }
      }
      final File tempFile =
                            restTemplate.execute(baseUrl + "/albums/{albumId}/image/{imageId}-{width}x{height}.jpg", HttpMethod.GET,
                                                 new RequestCallback() {
                                                   @Override
                                                   public void doWithRequest(final ClientHttpRequest request) throws IOException {
                                                     if (cachedFile.exists())
                                                       request.getHeaders().setIfModifiedSince(cachedFile.lastModified());
                                                   }
                                                 }, new ResponseExtractor<File>() {
                                                   @Override
                                                   public File extractData(final ClientHttpResponse response) throws IOException {
                                                     if (response.getStatusCode() == HttpStatus.NOT_MODIFIED)
                                                       return cachedFile;
                                                     final InputStream inputStream = response.getBody();
                                                     final File tempFile = new File(context.getCacheDir(), "temp-" + filename);
                                                     final FileOutputStream outputStream = new FileOutputStream(tempFile);
                                                     try {
                                                       final byte[] buffer = new byte[8192];
                                                       while (true) {
                                                         final int read = inputStream.read(buffer);
                                                         if (read < 0)
                                                           break;
                                                         outputStream.write(buffer, 0, read);
                                                       }
                                                     } finally {
                                                       outputStream.close();
                                                     }
                                                     final long lastModified = response.getHeaders().getLastModified();
                                                     if (lastModified > 0)
                                                       tempFile.setLastModified(lastModified);
                                                     return tempFile;
                                                   }
                                                 }, albumId, imageId, width, height);
      tempFile.renameTo(cachedFile);
    } finally {
      waitMap.remove(cachedFile);
    }
    return cachedFile;
  }

}
