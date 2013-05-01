package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;

@Data
public class AlbumList {
  private final Collection<AlbumEntry> albumNames = new ArrayList<AlbumEntry>();

}
