package ch.bergturbenthal.image.data;

import javax.xml.bind.annotation.XmlAttribute;

public class AlbumEntry {
  private String id;
  private String name;

  public AlbumEntry() {
  }

  public AlbumEntry(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  @XmlAttribute
  public String getId() {
    return id;
  }

  @XmlAttribute
  public String getName() {
    return name;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
