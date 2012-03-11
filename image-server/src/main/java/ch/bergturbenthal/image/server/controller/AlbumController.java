package ch.bergturbenthal.image.server.controller;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import ch.bergturbenthal.image.data.AlbumDetail;
import ch.bergturbenthal.image.data.AlbumEntry;
import ch.bergturbenthal.image.data.AlbumImageEntry;
import ch.bergturbenthal.image.data.AlbumList;
import ch.bergturbenthal.image.server.Album;
import ch.bergturbenthal.image.server.AlbumAccess;
import ch.bergturbenthal.image.server.AlbumImage;

@Controller
@RequestMapping("/albums")
public class AlbumController {
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

  private Map<String, AlbumData> foundAlbums = null;

  @Autowired
  private AlbumAccess albumAccess;

  @RequestMapping(value = "{albumid}", method = RequestMethod.GET)
  public AlbumDetail listAlbumContent(@PathVariable("albumid") final String albumid, final HttpServletResponse response) {
    final AlbumData savedAlbum = loadAlbums().get(albumid);
    if (savedAlbum == null) {
      response.setStatus(404);
      return null;
    }
    return getAlbumDetail(albumid, savedAlbum).detailData;
  }

  @RequestMapping(method = RequestMethod.GET)
  public AlbumList listAlbums() {
    final AlbumList albumList = new AlbumList();
    final Set<Entry<String, AlbumData>> keySet = loadAlbums().entrySet();
    for (final Entry<String, AlbumData> entry : keySet) {
      albumList.getAlbumNames().add(new AlbumEntry(entry.getKey(), entry.getValue().album.getName()));
    }
    return albumList;
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

}
