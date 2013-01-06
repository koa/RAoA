package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AlbumEntry {
  private String id;
  private String name;
  private final Collection<String> clients = new ArrayList<String>();
  private Date lastModified;
  private long repositorySize;

  public AlbumEntry(final String id, final String name) {
    this.id = id;
    this.name = name;
  }
}
