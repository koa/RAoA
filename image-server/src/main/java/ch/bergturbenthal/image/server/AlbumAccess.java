package ch.bergturbenthal.image.server;

import java.io.File;
import java.util.Map;

public interface AlbumAccess {
  /**
   * create a new Album
   * 
   * @param pathNames
   *          whished path-components
   * @return reference to created album
   */
  Album createAlbum(String[] pathNames);

  /**
   * read a found album
   * 
   * @param albumId
   *          id of the album
   * @return
   */
  Album getAlbum(String albumId);

  /**
   * import files of given directory into the albums.
   * 
   * @param importBaseDir
   */
  void importFiles(File importBaseDir);

  Map<String, Album> listAlbums();
}
