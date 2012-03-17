package ch.bergturbenthal.image.data;


public class AlbumEntry {
  private String id;
  private String name;

  public AlbumEntry() {
  }

  public AlbumEntry(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

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
