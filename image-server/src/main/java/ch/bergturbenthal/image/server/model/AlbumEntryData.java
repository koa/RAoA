package ch.bergturbenthal.image.server.model;

import java.util.Date;

import ch.bergturbenthal.image.data.model.Location;

import lombok.Data;

@Data
public class AlbumEntryData {
  private Date creationDate;
  private Location location;
}
