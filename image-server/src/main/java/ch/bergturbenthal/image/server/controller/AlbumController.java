package ch.bergturbenthal.image.server.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
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
  private static Logger logger = LoggerFactory.getLogger(AlbumController.class);
  @Autowired
  private AlbumAccess albumAccess;

  private final Semaphore concurrentConvertSemaphore = new Semaphore(4);

  @RequestMapping(method = RequestMethod.POST)
  @Override
  public @ResponseBody
  String createAlbum(@RequestBody final String[] pathComps) {
    return albumAccess.createAlbum(pathComps);
  }

  @RequestMapping(value = "import", method = RequestMethod.GET)
  public void importDirectory(@RequestParam("path") final String path, final HttpServletResponse response) throws IOException {
    albumAccess.importFiles(new File(path));
    System.gc();
    response.getWriter().println("Import finished");
  }

  @Override
  public AlbumDetail listAlbumContent(final String albumid) {
    final Album album = albumAccess.listAlbums().get(albumid);
    if (album == null)
      return null;
    final AlbumDetail ret = new AlbumDetail();
    ret.setId(albumid);
    ret.setName(album.getName());
    ret.getClients().addAll(album.listClients());
    ret.setAutoAddDate(album.getAutoAddBeginDate());
    final Map<String, AlbumImage> images = album.listImages();
    for (final Entry<String, AlbumImage> albumImageEntry : images.entrySet()) {
      final AlbumImageEntry entry = new AlbumImageEntry();
      final AlbumImage albumImage = albumImageEntry.getValue();
      entry.setId(albumImageEntry.getKey());
      fillAlbumImageEntry(albumImage, entry);
      ret.getImages().add(entry);
    }
    return ret;
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
    final Collection<AlbumEntry> albumNames = albumList.getAlbumNames();
    for (final Entry<String, Album> entry : albumAccess.listAlbums().entrySet()) {
      final Album album = entry.getValue();
      final AlbumEntry albumEntry = new AlbumEntry(entry.getKey(), album.getName());
      albumEntry.getClients().addAll(album.listClients());
      albumNames.add(albumEntry);
    }
    return albumList;
  }

  @Override
  public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException {
    final Album album = albumAccess.getAlbum(albumId);
    if (album == null) {
      return null;
    }
    final AlbumImage image = album.getImage(imageId);
    if (image == null) {
      return null;
    }
    final File cachedImage = image.getThumbnail();
    if (cachedImage != null)
      return makeImageResult(cachedImage, image, ifModifiedSince);
    try {
      concurrentConvertSemaphore.acquire();
      try {
        final File thumbnail = image.getThumbnail();
        return makeImageResult(thumbnail, image, ifModifiedSince);
      } finally {
        concurrentConvertSemaphore.release();
      }
    } catch (final InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }

  @RequestMapping(value = "{albumId}/image/{imageId}.jpg", method = RequestMethod.GET)
  public void readImage(@PathVariable("albumId") final String albumId, @PathVariable("imageId") final String imageId,
                        final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    final long modifiedTime = request.getDateHeader("If-Modified-Since");
    final ImageResult foundImage = readImage(albumId, imageId, modifiedTime > 0 ? new Date(modifiedTime) : null);
    if (foundImage == null) {
      response.setStatus(404);
      return;
    }

    if (!foundImage.isModified()) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }
    response.setContentType(foundImage.getMimeType());
    final Date created = foundImage.getCreated();
    if (created != null) {
      response.setDateHeader("created-at", created.getTime());
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
    final Album album = albumAccess.getAlbum(albumId);
    if (album == null)
      return;
    album.addClient(clientId);
  }

  @RequestMapping(value = "{albumId}/registerClient", method = RequestMethod.PUT)
  public void registerClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
    registerClient(albumId, clientId);
  }

  @Override
  public void setAutoAddDate(final String albumId, final Date autoAddDate) {
    final Album album = albumAccess.getAlbum(albumId);
    if (album == null)
      return;
    album.setAutoAddBeginDate(autoAddDate);
  }

  @RequestMapping(value = "{albumId}/setAutoAddDate", method = RequestMethod.PUT)
  public void setAutoAddDate(@PathVariable("albumId") final String albumId, @RequestBody final Date autoAddDate, final HttpServletResponse response) {
    setAutoAddDate(albumId, autoAddDate);
  }

  @Override
  public void unRegisterClient(final String albumId, final String clientId) {
    final Album album = albumAccess.getAlbum(albumId);
    if (album == null)
      return;
    album.removeClient(clientId);
  }

  @RequestMapping(value = "{albumId}/unRegisterClient", method = RequestMethod.PUT)
  public void unRegisterClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
    unRegisterClient(albumId, clientId);
  }

  private void fillAlbumImageEntry(final AlbumImage albumImage, final AlbumImageEntry entry) {
    entry.setName(albumImage.getName());
    entry.setVideo(albumImage.isVideo());
    entry.setLastModified(albumImage.lastModified());
    try {
      entry.setCaptureDate(albumImage.captureDate());
    } catch (final RuntimeException ex) {
      logger.warn("cannot read Datum from image " + albumImage.getName(), ex);
    }
  }

  private ImageResult makeImageResult(final File sourceFile, final AlbumImage image, final Date ifModifiedSince) {
    final Date lastModified = new Date(sourceFile.lastModified());
    if (ifModifiedSince == null || ifModifiedSince.before(lastModified))
      return ImageResult.makeModifiedResult(lastModified, image.captureDate(), new ImageResult.StreamSource() {

        @Override
        public InputStream getInputStream() throws IOException {
          return new FileInputStream(sourceFile);
        }
      }, image.isVideo() ? "video/mp4" : "image/jpeg");
    else
      return ImageResult.makeNotModifiedResult();
  }
}
