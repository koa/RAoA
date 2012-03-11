package ch.bergturbenthal.image.data;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AlbumDetail extends AlbumEntry {
  private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();

  @XmlElement(name = "image")
  public Collection<AlbumImageEntry> getImages() {
    return images;
  }
}
