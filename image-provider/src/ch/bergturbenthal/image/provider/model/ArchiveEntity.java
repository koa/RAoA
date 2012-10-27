package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "archives")
public class ArchiveEntity {
  @DatabaseField(id = true)
  private final String name;
  @ForeignCollectionField(eager = false)
  private final Collection<AlbumEntity> albums = new ArrayList<AlbumEntity>();

  ArchiveEntity() {
    name = null;
  }

  public ArchiveEntity(final String name) {
    this.name = name;
  }

  public Collection<AlbumEntity> getAlbums() {
    return albums;
  }

  public String getName() {
    return name;
  }

}
