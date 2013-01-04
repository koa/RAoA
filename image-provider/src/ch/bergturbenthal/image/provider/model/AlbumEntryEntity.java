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
  private final int id;
  @DatabaseField
  private final String commId;
  @DatabaseField(foreign = true, uniqueIndexName = "entry_name_index")
  private final AlbumEntity album;
  @DatabaseField(canBeNull = false, dataType = DataType.ENUM_STRING)
  private final AlbumEntryType type;
  @DatabaseField(canBeNull = false)
  private Date lastModified;
  @DatabaseField
  private Date captureDate;
  @DatabaseField
  private boolean deleted;

  public AlbumEntryEntity(final AlbumEntity album, final String name, final String commId, final AlbumEntryType type, final Date lastModified,
                          final Date captureDate) {
    this.album = album;
    this.name = name;
    this.commId = commId;
    this.type = type;
    this.lastModified = lastModified;
    this.captureDate = captureDate;
    deleted = false;
    this.id = -1;
  }

  protected AlbumEntryEntity() {
    name = null;
    album = null;
    type = null;
    commId = null;
    id = -1;
  }

  public AlbumEntity getAlbum() {
    return album;
  }

  @CursorField(Client.AlbumEntry.CAPTURE_DATE)
  public Date getCaptureDate() {
    return captureDate;
  }

  public String getCommId() {
    return commId;
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

  public boolean isDeleted() {
    return deleted;
  }

  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }

  public void setDeleted(final boolean deleted) {
    this.deleted = deleted;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

}
