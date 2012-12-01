package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import android.util.Log;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;
import ch.bergturbenthal.image.data.model.AlbumList;

public class ServerConnection {
  private static interface ConnectionCallable<V> {
    ResponseEntity<V> call(URL baseUrl) throws Exception;
  }

  private String instanceId;
  private final AtomicReference<Collection<URL>> connections = new AtomicReference<Collection<URL>>(Collections.<URL> emptyList());
  private final AtomicReference<SoftReference<Map<String, String>>> albumIds = new AtomicReference<SoftReference<Map<String, String>>>();
  private final RestTemplate restTemplate = new RestTemplate(true);
  private final Map<String, SoftReference<AlbumDetail>> albumDetailCache = new HashMap<String, SoftReference<AlbumDetail>>();

  private static final String[] DATE_FORMATS = new String[] { "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd-MMM-yy HH:mm:ss zzz",
                                                             "EEE MMM dd HH:mm:ss yyyy" };

  private static TimeZone GMT = TimeZone.getTimeZone("GMT");

  public AlbumDetail getAlbumDetail(final String albumName) {
    final SoftReference<AlbumDetail> cachedValue = albumDetailCache.get(albumName);
    if (cachedValue != null) {
      final AlbumDetail albumDetail = cachedValue.get();
      if (albumDetail != null)
        return albumDetail;
    }
    final String albumId = resolveAlbumName(albumName);
    final AlbumDetail albumDetail = callOne(new ConnectionCallable<AlbumDetail>() {

      @Override
      public ResponseEntity<AlbumDetail> call(final URL baseUrl) throws Exception {
        return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums/{albumId}.json", AlbumDetail.class, albumId);
      }
    });
    final Map<String, String> entryIdMap = new HashMap<String, String>();
    for (final AlbumImageEntry entry : albumDetail.getImages()) {
      entryIdMap.put(entry.getName(), entry.getId());
    }
    albumDetailCache.put(albumName, new SoftReference<AlbumDetail>(albumDetail));
    return albumDetail;
  }

  public String getInstanceId() {
    return instanceId;
  }

  /**
   * Album-Keys by album-names
   * 
   * @return
   */
  public Map<String, String> listAlbums() {
    return collectAlbums();
  }

  public boolean readThumbnail(final String albumName, final String fileId, final File tempFile, final File targetFile) {
    final String albumId = resolveAlbumName(albumName);
    return callOne(new ConnectionCallable<Boolean>() {

      @Override
      public ResponseEntity<Boolean> call(final URL baseUrl) throws Exception {

        return restTemplate.execute(baseUrl.toExternalForm() + "/albums/{albumId}/image/{imageId}.jpg", HttpMethod.GET, new RequestCallback() {
          @Override
          public void doWithRequest(final ClientHttpRequest request) throws IOException {
            if (targetFile.exists())
              request.getHeaders().setIfModifiedSince(targetFile.lastModified());
          }
        }, new ResponseExtractor<ResponseEntity<Boolean>>() {
          @Override
          public ResponseEntity<Boolean> extractData(final ClientHttpResponse response) throws IOException {
            if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
              return new ResponseEntity<Boolean>(Boolean.TRUE, response.getStatusCode());
            }
            final HttpHeaders headers = response.getHeaders();
            final String mimeType = headers.getContentType().toString();
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
            final OutputStream arrayOutputStream = new FileOutputStream(tempFile);
            try {
              final InputStream inputStream = response.getBody();
              final byte[] buffer = new byte[8192];
              while (true) {
                final int read = inputStream.read(buffer);
                if (read < 0)
                  break;
                arrayOutputStream.write(buffer, 0, read);
              }
            } finally {
              arrayOutputStream.close();
            }
            tempFile.renameTo(targetFile);
            if (lastModifiedDate != null)
              targetFile.setLastModified(lastModifiedDate.getTime());

            return new ResponseEntity<Boolean>(Boolean.TRUE, response.getStatusCode());
          }
        }, albumId, fileId);
      }
    }).booleanValue();

  }

  public void setInstanceId(final String instanceId) {
    this.instanceId = instanceId;
  }

  public void updateServerConnections(final Collection<URL> value) {
    connections.set(value);
  }

  private <V> V callOne(final ConnectionCallable<V> callable) {
    Throwable t = null;
    for (final URL connection : connections.get()) {
      try {
        final ResponseEntity<V> response;
        response = callable.call(connection);
        if (response != null && response.hasBody())
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

  private synchronized Map<String, String> collectAlbums() {
    final SoftReference<Map<String, String>> reference = albumIds.get();
    if (reference != null) {
      final Map<String, String> cachedMap = reference.get();
      if (cachedMap != null)
        return cachedMap;
    }
    final Map<String, String> ret = new HashMap<String, String>();
    final AlbumList albums = callOne(new ConnectionCallable<AlbumList>() {

      @Override
      public ResponseEntity<AlbumList> call(final URL baseUrl) throws Exception {
        return restTemplate.getForEntity(baseUrl.toExternalForm() + "/albums.json", AlbumList.class);
      }
    });
    for (final AlbumEntry entry : albums.getAlbumNames()) {
      ret.put(entry.getName(), entry.getId());
    }
    albumIds.set(new SoftReference<Map<String, String>>(ret));
    return ret;
  }

  private AlbumImageEntry findAlbumEntry(final String albumName, final String file) {
    AlbumImageEntry foundEntry = null;
    final AlbumDetail albumDetail = getAlbumDetail(albumName);
    for (final AlbumImageEntry albumImageEntry : albumDetail.getImages()) {
      if (albumImageEntry.getName().equals(file)) {
        foundEntry = albumImageEntry;
        break;
      }
    }
    return foundEntry;
  }

  private String resolveAlbumName(final String albumName) {
    return collectAlbums().get(albumName);
  }
}
