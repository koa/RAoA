package ch.bergturbenthal.image.data;

import java.util.ArrayList;
import java.util.Collection;

public class AlbumDetail extends AlbumEntry {
  private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();

  public Collection<AlbumImageEntry> getImages() {
    return images;
  }
}
