package ch.bergturbenthal.image.server;

import java.io.File;
import java.util.Map;

public interface AlbumAccess {
  Album getAlbum(String albumId);

  void importFiles(File importBaseDir);

  Map<String, Album> listAlbums();
}
