package ch.bergturbenthal.image.data;

import java.util.ArrayList;
import java.util.Collection;

public class AlbumList {
  private final Collection<AlbumEntry> albumNames = new ArrayList<AlbumEntry>();

  public Collection<AlbumEntry> getAlbumNames() {
    return albumNames;
  }
}
