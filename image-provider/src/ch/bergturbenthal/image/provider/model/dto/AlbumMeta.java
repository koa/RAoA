/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.Date;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.CursorField;
import ch.bergturbenthal.image.provider.util.ParcelUtil;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumMeta implements Parcelable {
  private Date lastModified;
  private String name;
  private boolean synced;
  private boolean shouldSync;
  private Date autoAddDate;
  private String thumbnailId;
  private Date albumDate;
  private String archiveName;
  private String albumId;
  private int entryCount;
  private long thumbnailSize;
  private long repositorySize;
  private long originalsSize;

  public static Parcelable.Creator<AlbumMeta> CREATOR = new Parcelable.Creator<AlbumMeta>() {

    @Override
    public AlbumMeta createFromParcel(final Parcel source) {
      final AlbumMeta ret = new AlbumMeta();
      ret.setLastModified(ParcelUtil.readDate(source));
      ret.setName(source.readString());
      ret.setSynced(ParcelUtil.readBoolean(source));
      ret.setShouldSync(ParcelUtil.readBoolean(source));
      ret.setAutoAddDate(ParcelUtil.readDate(source));
      ret.setThumbnailId(source.readString());
      ret.setAlbumDate(ParcelUtil.readDate(source));
      ret.setArchiveName(source.readString());
      ret.setAlbumId(source.readString());
      ret.setEntryCount(source.readInt());
      ret.setThumbnailSize(source.readLong());
      ret.setRepositorySize(source.readLong());
      ret.setOriginalsSize(source.readLong());
      return ret;
    }

    @Override
    public AlbumMeta[] newArray(final int size) {
      return new AlbumMeta[size];
    }
  };

  @Override
  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * Returns the albumDate.
   * 
   * @return the albumDate
   */
  @CursorField(Client.Album.ALBUM_CAPTURE_DATE)
  public Date getAlbumDate() {
    return albumDate;
  }

  /**
   * Returns the albumId.
   * 
   * @return the albumId
   */
  @CursorField(Client.Album.ID)
  public String getAlbumId() {
    return albumId;
  }

  /**
   * Returns the archiveName.
   * 
   * @return the archiveName
   */
  @CursorField(Client.Album.ARCHIVE_NAME)
  public String getArchiveName() {
    return archiveName;
  }

  /**
   * Returns the autoAddDate.
   * 
   * @return the autoAddDate
   */
  @CursorField(Client.Album.AUTOADD_DATE)
  public Date getAutoAddDate() {
    return autoAddDate;
  }

  /**
   * Returns the entryCount.
   * 
   * @return the entryCount
   */
  @CursorField(Client.Album.ENTRY_COUNT)
  public int getEntryCount() {
    return entryCount;
  }

  /**
   * Returns the lastModified.
   * 
   * @return the lastModified
   */
  public Date getLastModified() {
    return lastModified;
  }

  /**
   * Returns the name.
   * 
   * @return the name
   */
  @CursorField(Client.Album.NAME)
  public String getName() {
    return name;
  }

  /**
   * Returns the entriesSize.
   * 
   * @return the entriesSize
   */
  @CursorField(Client.Album.ORIGINALS_SIZE)
  public long getOriginalsSize() {
    return originalsSize;
  }

  /**
   * Returns the repositorySize.
   * 
   * @return the repositorySize
   */
  @CursorField(Client.Album.REPOSITORY_SIZE)
  public long getRepositorySize() {
    return repositorySize;
  }

  /**
   * Returns the thumbnailId.
   * 
   * @return the thumbnailId
   */
  public String getThumbnailId() {
    return thumbnailId;
  }

  /**
   * Returns the thumbnailSize.
   * 
   * @return the thumbnailSize
   */
  @CursorField(Client.Album.THUMBNAILS_SIZE)
  public long getThumbnailSize() {
    return thumbnailSize;
  }

  /**
   * Returns the shouldSync.
   * 
   * @return the shouldSync
   */
  @CursorField(Client.Album.SHOULD_SYNC)
  public boolean isShouldSync() {
    return shouldSync;
  }

  /**
   * Returns the synced.
   * 
   * @return the synced
   */
  @CursorField(Client.Album.SYNCED)
  public boolean isSynced() {
    return synced;
  }

  /**
   * Sets the albumDate.
   * 
   * @param albumDate
   *          the albumDate to set
   */
  public void setAlbumDate(final Date albumDate) {
    this.albumDate = albumDate;
  }

  /**
   * Sets the albumId.
   * 
   * @param albumId
   *          the albumId to set
   */
  public void setAlbumId(final String albumId) {
    this.albumId = albumId;
  }

  /**
   * Sets the archiveName.
   * 
   * @param archiveName
   *          the archiveName to set
   */
  public void setArchiveName(final String archiveName) {
    this.archiveName = archiveName;
  }

  /**
   * Sets the autoAddDate.
   * 
   * @param autoAddDate
   *          the autoAddDate to set
   */
  public void setAutoAddDate(final Date autoAddDate) {
    this.autoAddDate = autoAddDate;
  }

  /**
   * Sets the entryCount.
   * 
   * @param entryCount
   *          the entryCount to set
   */
  public void setEntryCount(final int entryCount) {
    this.entryCount = entryCount;
  }

  /**
   * Sets the lastModified.
   * 
   * @param lastModified
   *          the lastModified to set
   */
  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

  /**
   * Sets the name.
   * 
   * @param name
   *          the name to set
   */
  public void setName(final String name) {
    this.name = name;
  }

  /**
   * Sets the entriesSize.
   * 
   * @param entriesSize
   *          the entriesSize to set
   */
  public void setOriginalsSize(final long entriesSize) {
    this.originalsSize = entriesSize;
  }

  /**
   * Sets the repositorySize.
   * 
   * @param repositorySize
   *          the repositorySize to set
   */
  public void setRepositorySize(final long repositorySize) {
    this.repositorySize = repositorySize;
  }

  /**
   * Sets the shouldSync.
   * 
   * @param shouldSync
   *          the shouldSync to set
   */
  public void setShouldSync(final boolean shouldSync) {
    this.shouldSync = shouldSync;
  }

  /**
   * Sets the synced.
   * 
   * @param synced
   *          the synced to set
   */
  public void setSynced(final boolean synced) {
    this.synced = synced;
  }

  /**
   * Sets the thumbnailId.
   * 
   * @param thumbnailId
   *          the thumbnailId to set
   */
  public void setThumbnailId(final String thumbnailId) {
    this.thumbnailId = thumbnailId;
  }

  /**
   * Sets the thumbnailSize.
   * 
   * @param thumbnailSize
   *          the thumbnailSize to set
   */
  public void setThumbnailSize(final long thumbnailSize) {
    this.thumbnailSize = thumbnailSize;
  }

  @Override
  public void writeToParcel(final Parcel dest, final int flags) {
    ParcelUtil.writeDate(lastModified, dest);
    dest.writeString(name);
    ParcelUtil.writeBoolean(dest, synced);
    ParcelUtil.writeBoolean(dest, shouldSync);
    ParcelUtil.writeDate(autoAddDate, dest);
    dest.writeString(thumbnailId);
    ParcelUtil.writeDate(albumDate, dest);
    dest.writeString(archiveName);
    dest.writeString(albumId);
    dest.writeInt(entryCount);
    dest.writeLong(thumbnailSize);
    dest.writeLong(repositorySize);
    dest.writeLong(originalsSize);
  }
}
