package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlbumDetail extends AlbumEntry {
  private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();
  private final Collection<String> interestingClients = new ArrayList<String>();
  private Date autoAddDate;
}
