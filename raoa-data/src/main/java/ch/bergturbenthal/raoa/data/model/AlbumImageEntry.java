package ch.bergturbenthal.raoa.data.model;

import java.util.Collection;
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
  private Long thumbnailFileSize;

  private String cameraMake;
  private String cameraModel;
  private String caption;
  private Double exposureTime;
  private Double fNumber;
  private Double focalLength;
  private Integer iso;
  private Collection<String> keywords;
  private Location location;
  private Integer rating;
  private String editableMetadataHash;
}
