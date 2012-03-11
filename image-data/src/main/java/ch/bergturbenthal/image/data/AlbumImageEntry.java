package ch.bergturbenthal.image.data;

import javax.xml.bind.annotation.XmlAttribute;

public class AlbumImageEntry {
  private String id;

  @XmlAttribute
  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }
}
