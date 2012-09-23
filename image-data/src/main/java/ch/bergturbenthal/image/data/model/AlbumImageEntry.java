package ch.bergturbenthal.image.data.model;

import java.util.Date;

public class AlbumImageEntry {
  private String id;
  private String name;
  private boolean isVideo;
  private Date lastModified;

  public String getId() {
    return id;
  }

  public Date getLastModified() {
    return lastModified;
  }

  public String getName() {
    return name;
  }

  public boolean isVideo() {
    return isVideo;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setVideo(final boolean isVideo) {
    this.isVideo = isVideo;
  }
}
