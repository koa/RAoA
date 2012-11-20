package ch.bergturbenthal.image.provider.model.dto;

import java.util.Date;

public class AlbumEntryDetailDto extends AlbumEntryDto {
  private Date captureDate;

  public Date getCaptureDate() {
    return captureDate;
  }

  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }

}
