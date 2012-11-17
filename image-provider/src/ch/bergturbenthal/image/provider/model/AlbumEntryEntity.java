package ch.bergturbenthal.image.provider.model;

import java.util.Date;

import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.CursorField;

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
  @DatabaseField(canBeNull = false)
  private final Date lastModified;

  public AlbumEntryEntity(final AlbumEntity album, final String name, final AlbumEntryType type, final Date lastModified) {
    this.album = album;
    this.name = name;
    this.type = type;
    this.lastModified = lastModified;
  }

  protected AlbumEntryEntity() {
    name = null;
    album = null;
    type = null;
    lastModified = null;
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  @CursorField(Client.AlbumEntry.ID)
  public int getId() {
    return id;
  }

  @CursorField(Client.AlbumEntry.LAST_MODIFIED)
  public Date getLastModified() {
    return lastModified;
  }

  @CursorField(Client.AlbumEntry.NAME)
  public String getName() {
    return name;
  }

  @CursorField(Client.AlbumEntry.ENTRY_TYPE)
  public AlbumEntryType getType() {
    return type;
  }

}
