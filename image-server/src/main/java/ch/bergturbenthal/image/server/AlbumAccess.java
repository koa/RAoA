package ch.bergturbenthal.image.server;

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;

public interface AlbumAccess {
  /**
   * create a new Album
   * 
   * @param pathNames
   *          whished path-components
   * @return id of created album
   */
  String createAlbum(String[] pathNames);

  /**
   * read a found album
   * 
   * @param albumId
   *          id of the album
   * @return
   */
  Album getAlbum(String albumId);

  /**
   * Unique ID of this Collection
   * 
   * @return collection-id
   */
  String getCollectionId();

  /**
   * Unique ID of this running instance (Server)
   * 
   * @return instance-id
   */
  String getInstanceId();

  Repository getMetaRepository();

  /**
   * import files of given directory into the albums.
   * 
   * @param importBaseDir
   */
  void importFiles(File importBaseDir);

  Map<String, Album> listAlbums();
}
