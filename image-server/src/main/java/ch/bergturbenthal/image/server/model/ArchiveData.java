/*
 * (c) 2012 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.server.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * TODO: add type comment.
 * 
 */
public class ArchiveData {
  private String archiveName;
  private final Map<String, Collection<String>> albumPerStorage = new TreeMap<String, Collection<String>>();

  /**
   * Returns the albumPerStorage.
   * 
   * @return the albumPerStorage
   */
  public Map<String, Collection<String>> getAlbumPerStorage() {
    return albumPerStorage;
  }

  /**
   * Returns the archiveName.
   * 
   * @return the archiveName
   */
  public String getArchiveName() {
    return archiveName;
  }

  /**
   * Sets the archiveName.
   * 
   * @param archiveName
   *          the archiveName to set
   */
  public void setArchiveName(final String archiveName) {
    this.archiveName = archiveName;
  }

}
