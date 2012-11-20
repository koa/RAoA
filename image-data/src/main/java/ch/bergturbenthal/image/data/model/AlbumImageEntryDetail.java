package ch.bergturbenthal.image.data.model;

import java.util.Date;

public class AlbumImageEntryDetail extends AlbumImageEntry {
  private Date captureDate;

  public Date getCaptureDate() {
    return captureDate;
  }

  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }
}
