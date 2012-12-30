package ch.bergturbenthal.image.provider.model.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AlbumDto {
  private Date autoAddDate;
  private Date lastModified;
  private final Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();

  public Date getAutoAddDate() {
    return autoAddDate;
  }

  public Map<String, AlbumEntryDto> getEntries() {
    return entries;
  }

  public Date getLastModified() {
    return lastModified;
  }

  public void setAutoAddDate(final Date autoAddDate) {
    this.autoAddDate = autoAddDate;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

}
