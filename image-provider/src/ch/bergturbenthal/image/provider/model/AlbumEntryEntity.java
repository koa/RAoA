package ch.bergturbenthal.image.provider.model;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "album_entry")
public class AlbumEntryEntity {
  @DatabaseField(canBeNull = false, uniqueIndexName = "entry_name_index")
  private final String name;
  @DatabaseField(generatedId = true)
  private final int id = -1;
  @DatabaseField(foreign = true, uniqueIndexName = "entry_name_index")
  private final AlbumEntity album;
  @DatabaseField(canBeNull = false, dataType = DataType.ENUM_STRING)
  private final AlbumEntryType type;

  public AlbumEntryEntity(final AlbumEntity album, final String name, final AlbumEntryType type) {
    this.album = album;
    this.name = name;
    this.type = type;
  }

  protected AlbumEntryEntity() {
    name = null;
    album = null;
    type = null;
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public AlbumEntryType getType() {
    return type;
  }

}
