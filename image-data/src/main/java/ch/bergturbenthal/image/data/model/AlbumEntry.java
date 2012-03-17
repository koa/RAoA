package ch.bergturbenthal.image.data.model;

import java.util.Date;

public class AlbumEntry {
  private String id;
  private String name;
  private Date firstPhotoDate;
  private Date lastPhotoDate;
  private int photoCount;

  public AlbumEntry() {
  }

  public AlbumEntry(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public Date getFirstPhotoDate() {
    return firstPhotoDate;
  }

  public String getId() {
    return id;
  }

  public Date getLastPhotoDate() {
    return lastPhotoDate;
  }

  public String getName() {
    return name;
  }

  public int getPhotoCount() {
    return photoCount;
  }

  public void setFirstPhotoDate(final Date firstPhotoDate) {
    this.firstPhotoDate = firstPhotoDate;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setLastPhotoDate(final Date lastPhotoDate) {
    this.lastPhotoDate = lastPhotoDate;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setPhotoCount(final int photoCount) {
    this.photoCount = photoCount;
  }
}
