package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;

public class AlbumEntry {
  private String id;
  private String name;
  private final Collection<String> clients = new ArrayList<String>();

  public AlbumEntry() {
  }

  public AlbumEntry(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public Collection<String> getClients() {
    return clients;
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
