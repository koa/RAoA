package ch.bergturbenthal.image.provider.model;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "clients")
public class ClientEntity {
  @DatabaseField(id = true)
  private final UUID id;
  @DatabaseField
  private final String name;
  @DatabaseField(foreign = true)
  private final AlbumEntity album;

  ClientEntity() {
    name = null;
    album = null;
    id = null;
  }

  public ClientEntity(final AlbumEntity album, final String name) {
    this.album = album;
    this.name = name;
    id = UUID.randomUUID();
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
