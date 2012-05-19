package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class AlbumDetail extends AlbumEntry {
  private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();
  private final Collection<String> interestingClients = new ArrayList<String>();
  private Date autoAddDate;

  public Date getAutoAddDate() {
    return autoAddDate;
  }

  public Collection<AlbumImageEntry> getImages() {
    return images;
  }

  public Collection<String> getInterestingClients() {
    return interestingClients;
  }

  public void setAutoAddDate(final Date autoAddDate) {
    this.autoAddDate = autoAddDate;
  }
}
