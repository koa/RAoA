/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.Map;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumEntries {
  private Map<String, AlbumEntryDto> entries;

  /**
   * Returns the entries.
   * 
   * @return the entries
   */
  public Map<String, AlbumEntryDto> getEntries() {
    return entries;
  }

  /**
   * Sets the entries.
   * 
   * @param entries
   *          the entries to set
   */
  public void setEntries(final Map<String, AlbumEntryDto> entries) {
    this.entries = entries;
  }

}
