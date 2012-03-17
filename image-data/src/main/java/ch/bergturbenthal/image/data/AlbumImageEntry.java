package ch.bergturbenthal.image.data;

import java.util.Date;

public class AlbumImageEntry {
  private String id;
  private Date creationDate;
  private String name;

  public Date getCreationDate() {
    return creationDate;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setCreationDate(final Date creationDate) {
    this.creationDate = creationDate;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
