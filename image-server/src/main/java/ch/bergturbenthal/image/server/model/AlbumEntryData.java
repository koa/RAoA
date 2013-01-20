package ch.bergturbenthal.image.server.model;

import java.util.Collection;
import java.util.Date;

import lombok.Data;
import ch.bergturbenthal.image.data.model.Location;

@Data
public class AlbumEntryData {
  private String cameraMake;
  private String cameraModel;
  private String caption;
  private Date creationDate;
  private Double exposureTime;
  private Double fNumber;
  private Double focalLength;
  private Integer iso;
  private Collection<String> keywords;
  private Location location;
  private Integer rating;
  private String editableMetadataHash;
}
