package ch.bergturbenthal.image.data.model;

public class AlbumImageEntry {
  private String id;
  private String name;
  private boolean isVideo;

  public String getId() {
    return id;
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

  public void setName(final String name) {
    this.name = name;
  }

  public void setVideo(final boolean isVideo) {
    this.isVideo = isVideo;
  }
}
