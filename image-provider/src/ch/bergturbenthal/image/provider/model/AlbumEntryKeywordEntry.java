package ch.bergturbenthal.image.provider.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class AlbumEntryKeywordEntry {
  @DatabaseField(generatedId = true)
  private final int id;
  @DatabaseField(foreign = true, uniqueIndexName = "entry_keyword_name_index")
  private final AlbumEntryEntity image;
  @DatabaseField(canBeNull = false, uniqueIndexName = "entry_keyword_name_index")
  private final String keyword;
  @DatabaseField
  private boolean added = false;
  @DatabaseField
  private boolean deleted = false;

  public AlbumEntryKeywordEntry(final AlbumEntryEntity image, final String keyword) {
    this.image = image;
    this.keyword = keyword;
    id = -1;
  }

  protected AlbumEntryKeywordEntry() {
    image = null;
    keyword = null;
    id = -1;
  }

  public int getId() {
    return id;
  }

  public AlbumEntryEntity getImage() {
    return image;
  }

  public String getKeyword() {
    return keyword;
  }

  public boolean isAdded() {
    return added;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setAdded(final boolean added) {
    this.added = added;
  }

  public void setDeleted(final boolean deleted) {
    this.deleted = deleted;
  }

}
