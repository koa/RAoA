package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "albums")
public class AlbumEntity {

  @DatabaseField(foreign = true, indexName = "name_index", uniqueIndex = true)
  private final ArchiveEntity archive;
  @DatabaseField(canBeNull = false, indexName = "name_index", uniqueIndex = true)
  private final String name;
  @DatabaseField(generatedId = true)
  private int id;
  @ForeignCollectionField(eager = true)
  private final Collection<ClientEntity> interestingClients = new ArrayList<ClientEntity>();

  @DatabaseField
  private Date autoAddDate;

  @ForeignCollectionField(eager = false)
  private final Collection<AlbumEntryEntity> entries = new ArrayList<AlbumEntryEntity>();

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

  public Date getAutoAddDate() {
    return autoAddDate;
  }

  public Collection<AlbumEntryEntity> getEntries() {
    return entries;
  }

  public int getId() {
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

}
