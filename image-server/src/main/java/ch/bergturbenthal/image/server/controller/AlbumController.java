package ch.bergturbenthal.image.server.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.image.data.api.ImageResult;
import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumEntry;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;
import ch.bergturbenthal.image.data.model.AlbumList;
import ch.bergturbenthal.image.server.Album;
import ch.bergturbenthal.image.server.AlbumAccess;
import ch.bergturbenthal.image.server.AlbumImage;

@Controller
@RequestMapping("/albums")
public class AlbumController implements ch.bergturbenthal.image.data.api.Album {
  private static class AlbumData {
    private Album album;
    private AlbumDetail detailData = null;
    private Map<String, AlbumImage> images;
  }

  private static String sha1(final String text) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(text.getBytes("utf-8"), 0, text.length());
      final Base32 base32 = new Base32();
      return base32.encodeToString(md.digest()).toLowerCase();
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException("cannot make sha1 of " + text, e);
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException("cannot make sha1 of " + text, e);
    }
  }

  @Autowired
  private AlbumAccess albumAccess;

  private final Semaphore concurrentConvertSemaphore = new Semaphore(4);
  private Map<String, AlbumData> foundAlbums = null;

  @Override
  public AlbumDetail listAlbumContent(final String albumid) {
    final AlbumData savedAlbum = loadAlbums().get(albumid);
    if (savedAlbum == null) {
      return null;
    }
    return getAlbumDetail(albumid, savedAlbum).detailData;
  }

  @RequestMapping(value = "{albumid}", method = RequestMethod.GET)
  public @ResponseBody
  AlbumDetail listAlbumContent(@PathVariable("albumid") final String albumid, final HttpServletResponse response) {
    final AlbumDetail content = listAlbumContent(albumid);
    if (content == null)
      response.setStatus(404);
    return content;
  }

  @Override
  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  AlbumList listAlbums() {
    final AlbumList albumList = new AlbumList();
    final Set<Entry<String, AlbumData>> keySet = loadAlbums().entrySet();
    for (final Entry<String, AlbumData> entry : keySet) {
      final AlbumEntry albumEntry = new AlbumEntry(entry.getKey(), entry.getValue().album.getName());
      albumEntry.getClients().addAll(entry.getValue().album.listClients());
      albumList.getAlbumNames().add(albumEntry);
    }
    return albumList;
  }

  @Override
  public ImageResult
      readImage(final String albumId, final String imageId, final int width, final int height, final Date ifModifiedSince) throws IOException {
    final AlbumData savedAlbum = loadAlbums().get(albumId);
    if (savedAlbum == null) {
      return null;
    }
    final AlbumData albumData = getAlbumDetail(albumId, savedAlbum);
    final AlbumImage image = albumData.images.get(imageId);
    if (image == null) {
      return null;
    }
    final File cachedImage = image.getThumbnail(width, height, false, true);
    if (cachedImage != null)
      return makeImageResult(cachedImage);
    try {
      concurrentConvertSemaphore.acquire();
      try {
        final File thumbnail = image.getThumbnail(width, height, false, false);
        return makeImageResult(thumbnail);
      } finally {
        concurrentConvertSemaphore.release();
      }
    } catch (final InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }

  @RequestMapping(value = "{albumId}/image/{imageId}-{width}x{height}.jpg", method = RequestMethod.GET)
  public void readImage(@PathVariable("albumId") final String albumId, @PathVariable("imageId") final String imageId,
                        @PathVariable("width") final int width, @PathVariable("height") final int height, final HttpServletRequest request,
                        final HttpServletResponse response) throws IOException {
    final long modifiedTime = request.getDateHeader("If-Modified-Since");
    final ImageResult foundImage = readImage(albumId, imageId, width, height, modifiedTime > 0 ? new Date(modifiedTime) : null);
    if (foundImage == null) {
      response.setStatus(404);
      return;
    }
    response.setContentType("image/jpeg");
    if (foundImage.getLastModified().getTime() <= modifiedTime) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }
    final InputStream inputStream = foundImage.getDataStream();
    try {
      response.setDateHeader("last-modified", foundImage.getLastModified().getTime());
      final ServletOutputStream outputStream = response.getOutputStream();
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
    } finally {
      inputStream.close();
    }
  }

  @Override
  public void registerClient(final String albumId, final String clientId) {
    final AlbumData albumData = loadAlbums().get(albumId);
    if (albumData == null)
      return;
    albumData.album.addClient(clientId);
  }

  @RequestMapping(value = "{albumId}/registerClient", method = RequestMethod.PUT)
  public void registerClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
    registerClient(albumId, clientId);
  }

  @Override
  public void unRegisterClient(final String albumId, final String clientId) {
    final AlbumData albumData = loadAlbums().get(albumId);
    if (albumData == null)
      return;
    albumData.album.removeClient(clientId);
  }

  @RequestMapping(value = "{albumId}/unRegisterClient", method = RequestMethod.PUT)
  public void unRegisterClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
    unRegisterClient(albumId, clientId);
  }

  private synchronized AlbumData getAlbumDetail(final String albumid, final AlbumData savedAlbum) {
    if (savedAlbum.detailData == null) {
      final AlbumDetail detail = new AlbumDetail();
      detail.setName(savedAlbum.album.getName());
      detail.setId(albumid);
      final Map<String, AlbumImage> images = new HashMap<String, AlbumImage>();
      for (final AlbumImage albumImage : savedAlbum.album.listImages()) {
        final String id = sha1(albumImage.getName());
        images.put(id, albumImage);
        final AlbumImageEntry entry = new AlbumImageEntry();
        entry.setId(id);
        entry.setName(albumImage.getName());
        entry.setCreationDate(albumImage.captureDate());
        detail.getImages().add(entry);
      }
      savedAlbum.detailData = detail;
      savedAlbum.images = images;
    }
    return savedAlbum;
  }

  private synchronized Map<String, AlbumData> loadAlbums() {
    if (foundAlbums == null) {
      foundAlbums = new HashMap<String, AlbumController.AlbumData>();
      final Collection<Album> albums = albumAccess.listAlbums();
      for (final Album album : albums) {
        final AlbumData data = new AlbumData();
        data.album = album;
        foundAlbums.put(sha1(album.getName()), data);
      }
    }
    return foundAlbums;
  }

  private ImageResult makeImageResult(final File sourceFile) {
    return new ImageResult(new Date(sourceFile.lastModified()), new ImageResult.StreamSource() {

      @Override
      public InputStream getInputStream() throws IOException {
        return new FileInputStream(sourceFile);
      }
    });
  }

}
