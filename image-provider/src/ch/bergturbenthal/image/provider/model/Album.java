package ch.bergturbenthal.image.provider.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "albums")
public class Album {
  @DatabaseField(canBeNull = false)
  private String name;
  @DatabaseField(id = true)
  private final String id;

  public Album(final String id) {
    super();
    this.id = id;
  }

  Album() {
    id = null;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

}
