package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;

public class AlbumDetail extends AlbumEntry {
  private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();
  private final Collection<String> interestingClients = new ArrayList<String>();

  public Collection<AlbumImageEntry> getImages() {
    return images;
  }

  public Collection<String> getInterestingClients() {
    return interestingClients;
  }
}
