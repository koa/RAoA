package ch.bergturbenthal.image.data;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AlbumList {
  private final Collection<AlbumEntry> albumNames = new ArrayList<AlbumEntry>();

  @XmlElement(name = "album")
  public Collection<AlbumEntry> getAlbumNames() {
    return albumNames;
  }
}
