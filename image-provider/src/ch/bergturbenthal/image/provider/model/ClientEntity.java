package ch.bergturbenthal.image.provider.model;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "clients")
public class ClientEntity extends AbstractCacheableEntity<UUID> {
  @DatabaseField(id = true)
  private final UUID id;
  @DatabaseField
  private final String name;
  @DatabaseField(foreign = true)
  private final AlbumEntity album;

  public ClientEntity(final AlbumEntity album, final String name) {
    super(true);
    this.album = album;
    this.name = name;
    id = UUID.randomUUID();
  }

  ClientEntity() {
    super(false);
    name = null;
    album = null;
    id = null;
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
