package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "albums")
public class AlbumEntity {
  @DatabaseField(foreign = true)
  private final ArchiveEntity archive;
  @DatabaseField(canBeNull = false)
  private String name;
  @DatabaseField(id = true)
  private final String id;
  @ForeignCollectionField(eager = true)
  private final Collection<ClientEntity> interestingClients = new ArrayList<ClientEntity>();
  @DatabaseField
  private Date autoAddDate;

  @ForeignCollectionField(eager = false)
  private final Collection<AlbumEntryEntity> entries = new ArrayList<AlbumEntryEntity>();

  AlbumEntity() {
    id = null;
    archive = null;
  }

  public AlbumEntity(final ArchiveEntity archive, final String id) {
    this.archive = archive;
    this.id = id;
  }

  public ArchiveEntity getArchive() {
    return archive;
  }

  public Date getAutoAddDate() {
    return autoAddDate;
  }

  public Collection<AlbumEntryEntity> getEntries() {
    return entries;
  }

  public String getId() {
    return id;
  }

  public Collection<ClientEntity> getInterestingClients() {
    return interestingClients;
  }

  public String getName() {
    return name;
  }

  public void setAutoAddDate(final Date autoAddDate) {
    this.autoAddDate = autoAddDate;
  }

  public void setName(final String name) {
    this.name = name;
  }

}
