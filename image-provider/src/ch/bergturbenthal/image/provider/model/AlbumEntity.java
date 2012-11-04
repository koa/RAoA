package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import ch.bergturbenthal.image.provider.Data;
import ch.bergturbenthal.image.provider.map.CursorField;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "albums")
public class AlbumEntity {

  @DatabaseField(foreign = true, uniqueIndexName = "name_index")
  private final ArchiveEntity archive;

  @DatabaseField(canBeNull = false, uniqueIndexName = "name_index")
  private final String name;

  @DatabaseField(generatedId = true)
  private int id;

  @ForeignCollectionField(eager = true)
  private final Collection<ClientEntity> interestingClients = new ArrayList<ClientEntity>();

  @DatabaseField
  private Date autoAddDate;

  @DatabaseField
  private boolean syncThumbnails = false;

  @ForeignCollectionField(eager = false)
  private final Collection<AlbumEntryEntity> entries = new ArrayList<AlbumEntryEntity>();

  @DatabaseField
  private boolean shouldSync = false;

  @DatabaseField
  private boolean synced = false;

  public AlbumEntity(final ArchiveEntity archive, final String name) {
    this.archive = archive;
    this.name = name;
  }

  AlbumEntity() {
    id = -1;
    archive = null;
    name = null;
  }

  public ArchiveEntity getArchive() {
    return archive;
  }

  @CursorField(Data.Album.AUTOADD_DATE)
  public Date getAutoAddDate() {
    return autoAddDate;
  }

  public Collection<AlbumEntryEntity> getEntries() {
    return entries;
  }

  @CursorField(Data.Album.ID)
  public int getId() {
    return id;
  }

  public Collection<ClientEntity> getInterestingClients() {
    return interestingClients;
  }

  @CursorField(Data.Album.NAME)
  public String getName() {
    return name;
  }

  public boolean getSyncThumbnails() {
    return syncThumbnails;
  }

  @CursorField(Data.Album.SHOULD_SYNC)
  public boolean isShouldSync() {
    return shouldSync;
  }

  @CursorField(Data.Album.SYNCED)
  public boolean isSynced() {
    return synced;
  }

  public void setAutoAddDate(final Date autoAddDate) {
    this.autoAddDate = autoAddDate;
  }

  public void setShouldSync(final boolean shouldSync) {
    this.shouldSync = shouldSync;
  }

  public void setSynced(final boolean isSynced) {
    this.synced = isSynced;
  }

  public void setSyncThumbnails(final boolean syncThumbnails) {
    this.syncThumbnails = syncThumbnails;
  }

}
