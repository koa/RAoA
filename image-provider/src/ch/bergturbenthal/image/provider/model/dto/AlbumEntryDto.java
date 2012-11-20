package ch.bergturbenthal.image.provider.model.dto;

import java.util.Date;

import ch.bergturbenthal.image.provider.model.AlbumEntryType;

public class AlbumEntryDto {
  private Date lastModified;
  private AlbumEntryType entryType;

  public AlbumEntryType getEntryType() {
    return entryType;
  }

  public Date getLastModified() {
    return lastModified;
  }

  public void setEntryType(final AlbumEntryType entryType) {
    this.entryType = entryType;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

}
