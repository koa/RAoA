package ch.bergturbenthal.image.provider.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "album_entry")
public class AlbumEntryEntity {
  @DatabaseField(canBeNull = false)
  private final String name;
  @DatabaseField(id = true)
  private final String id;
  @DatabaseField(foreign = true)
  private final AlbumEntity album;

  protected AlbumEntryEntity() {
    id = null;
    name = null;
    album = null;
  }

  public AlbumEntryEntity(final AlbumEntity album, final String id, final String name) {
    this.album = album;
    this.id = id;
    this.name = name;
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

}
