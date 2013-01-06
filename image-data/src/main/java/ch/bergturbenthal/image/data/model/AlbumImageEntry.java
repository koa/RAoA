package ch.bergturbenthal.image.data.model;

import java.util.Date;

import lombok.Data;

@Data
public class AlbumImageEntry {
  private String id;
  private String name;
  private boolean isVideo;
  private Date lastModified;
  private Date captureDate;
  private long originalFileSize;
  private long thumbnailFileSize;

}
