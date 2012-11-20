package ch.bergturbenthal.image.provider.model.dto;

import java.util.Date;

import ch.bergturbenthal.image.provider.model.AlbumEntryType;

public class AlbumEntryDto {
  private Date lastModified;
  private AlbumEntryType entryType;
  private Date captureDate;

  /**
   * Returns the captureDate.
   * 
   * @return the captureDate
   */
  public Date getCaptureDate() {
    return captureDate;
  }

  public AlbumEntryType getEntryType() {
    return entryType;
  }

  public Date getLastModified() {
    return lastModified;
  }

  /**
   * Sets the captureDate.
   * 
   * @param captureDate
   *          the captureDate to set
   */
  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }

  public void setEntryType(final AlbumEntryType entryType) {
    this.entryType = entryType;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

}
