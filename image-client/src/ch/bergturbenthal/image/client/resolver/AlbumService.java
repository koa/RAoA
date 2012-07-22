package ch.bergturbenthal.image.client.resolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeSet;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import ch.bergturbenthal.image.data.api.Album;
import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumList;

public class AlbumService implements Album {
  private final String baseUrl;
  private final RestTemplate restTemplate;

  private static final String[] DATE_FORMATS = new String[] { "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd-MMM-yy HH:mm:ss zzz",
                                                             "EEE MMM dd HH:mm:ss yyyy" };

  private static TimeZone GMT = TimeZone.getTimeZone("GMT");

  public AlbumService(final String baseUrl) {
    this.baseUrl = baseUrl;
    restTemplate = new RestTemplate();
  }

  @Override
  public String createAlbum(final String[] pathComps) {
    final ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/albums", pathComps, String.class);
    if (response.hasBody())
      return response.getBody();
    throw new RuntimeException("Response without body. Status: " + response.getStatusCode());
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

  public Collection<String> listKnownClientNames() {
    final Collection<String> ret = new TreeSet<String>();
    for (final AlbumEntry album : listAlbums().getAlbumNames()) {
      ret.addAll(album.getClients());
    }
    return ret;
  }

  @Override
  public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) {
    return restTemplate.execute(baseUrl + "/albums/{albumId}/image/{imageId}.jpg", HttpMethod.GET, new RequestCallback() {
      @Override
      public void doWithRequest(final ClientHttpRequest request) throws IOException {
        if (ifModifiedSince != null)
          request.getHeaders().setIfModifiedSince(ifModifiedSince.getTime());
      }
    }, new ResponseExtractor<ImageResult>() {
      @Override
      public ImageResult extractData(final ClientHttpResponse response) throws IOException {
        if (response.getStatusCode() == HttpStatus.NOT_MODIFIED)
          return ImageResult.makeNotModifiedResult();
        final HttpHeaders headers = response.getHeaders();
        final long lastModified = headers.getLastModified();
        final Date lastModifiedDate;
        if (lastModified > 0)
          lastModifiedDate = new Date(lastModified);
        else
          lastModifiedDate = null;
        Date createDate = null;
        final String createDateString = headers.getFirst("created-at");
        if (createDateString != null) {
          for (final String dateFormat : DATE_FORMATS) {
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
            simpleDateFormat.setTimeZone(GMT);
            try {
              createDate = simpleDateFormat.parse(createDateString);
              break;
            } catch (final ParseException e) {
              // ignore
            }
          }
          if (createDate == null)
            throw new IllegalArgumentException("Cannot parse date value \"" + createDateString + "\" for \"created-at\" header");
        }
        final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        final InputStream inputStream = response.getBody();
        final byte[] buffer = new byte[8192];
        while (true) {
          final int read = inputStream.read(buffer);
          if (read < 0)
            break;
          arrayOutputStream.write(buffer, 0, read);
        }
        return ImageResult.makeModifiedResult(lastModifiedDate, createDate, new ImageResult.StreamSource() {
          @Override
          public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(arrayOutputStream.toByteArray());
          }
        });
      }
    }, albumId, imageId);

  }

  @Override
  public void registerClient(final String albumId, final String clientId) {
    restTemplate.put(baseUrl + "/albums/{albumId}/registerClient", clientId, albumId);
  }

  @Override
  public void setAutoAddDate(final String albumId, final Date autoAddDate) {
    restTemplate.put(baseUrl + "/albums/{albumId}/setAutoAddDate", autoAddDate, albumId);
  }

  @Override
  public void unRegisterClient(final String albumId, final String clientId) {
    restTemplate.put(baseUrl + "/albums/{albumId}/unRegisterClient", clientId, albumId);
  }

}
