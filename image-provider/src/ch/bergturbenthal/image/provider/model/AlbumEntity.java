package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "albums")
public class AlbumEntity extends AbstractCacheableEntity<String> {
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

  public AlbumEntity(final String id) {
    super(true);
    this.id = id;
  }

  AlbumEntity() {
    super(false);
    id = null;
  }

  public Date getAutoAddDate() {
    return autoAddDate;
  }

  @Override
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
    checkDifference(this.autoAddDate, autoAddDate);
    this.autoAddDate = autoAddDate;
  }

  public void setName(final String name) {
    checkDifference(this.name, name);
    this.name = name;
  }

}
