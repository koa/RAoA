package ch.bergturbenthal.image.provider.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.CursorField;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "album_entries")
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
  @DatabaseField
  private long originalSize;
  @DatabaseField
  private long thumbnailSize;
  @DatabaseField
  private String editableMetadataHash;
  @DatabaseField
  private String cameraMake;
  @DatabaseField
  private String cameraModel;
  @DatabaseField
  private Double exposureTime;
  @DatabaseField
  private String metaCaption;
  @DatabaseField
  private boolean metaCaptionModified = false;
  @DatabaseField
  private Double fNumber;
  @DatabaseField
  private Double focalLength;
  @DatabaseField
  private Integer iso;
  @DatabaseField
  private Integer metaRating;
  @DatabaseField
  private boolean metaRatingModified = false;
  @ForeignCollectionField(eager = true)
  private final Collection<AlbumEntryKeywordEntry> keywords = new ArrayList<AlbumEntryKeywordEntry>();

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

  @CursorField(Client.AlbumEntry.CAMERA_MAKE)
  public String getCameraMake() {
    return cameraMake;
  }

  @CursorField(Client.AlbumEntry.CAMERA_MODEL)
  public String getCameraModel() {
    return cameraModel;
  }

  @CursorField(Client.AlbumEntry.CAPTURE_DATE)
  public Date getCaptureDate() {
    return captureDate;
  }

  public String getCommId() {
    return commId;
  }

  public String getEditableMetadataHash() {
    return editableMetadataHash;
  }

  @CursorField(Client.AlbumEntry.EXPOSURE_TIME)
  public Double getExposureTime() {
    return exposureTime;
  }

  @CursorField(Client.AlbumEntry.F_NUMBER)
  public Double getfNumber() {
    return fNumber;
  }

  @CursorField(Client.AlbumEntry.FOCAL_LENGTH)
  public Double getFocalLength() {
    return focalLength;
  }

  @CursorField(Client.AlbumEntry.ID)
  public int getId() {
    return id;
  }

  @CursorField(Client.AlbumEntry.ISO)
  public Integer getIso() {
    return iso;
  }

  public Collection<AlbumEntryKeywordEntry> getKeywords() {
    return keywords;
  }

  @CursorField(Client.AlbumEntry.LAST_MODIFIED)
  public Date getLastModified() {
    return lastModified;
  }

  @CursorField(Client.AlbumEntry.META_CAPTION)
  public String getMetaCaption() {
    return metaCaption;
  }

  @CursorField(Client.AlbumEntry.META_RATING)
  public Integer getMetaRating() {
    return metaRating;
  }

  @CursorField(Client.AlbumEntry.NAME)
  public String getName() {
    return name;
  }

  @CursorField(Client.AlbumEntry.ORIGINAL_SIZE)
  public long getOriginalSize() {
    return originalSize;
  }

  @CursorField(Client.AlbumEntry.THUMBNAIL_SIZE)
  public long getThumbnailSize() {
    return thumbnailSize;
  }

  @CursorField(Client.AlbumEntry.ENTRY_TYPE)
  public AlbumEntryType getType() {
    return type;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public boolean isMetaCaptionModified() {
    return metaCaptionModified;
  }

  public boolean isMetaRatingModified() {
    return metaRatingModified;
  }

  public void setCameraMake(final String cameraMake) {
    this.cameraMake = cameraMake;
  }

  public void setCameraModel(final String cameraModel) {
    this.cameraModel = cameraModel;
  }

  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }

  public void setDeleted(final boolean deleted) {
    this.deleted = deleted;
  }

  public void setEditableMetadataHash(final String editableMetadataHash) {
    this.editableMetadataHash = editableMetadataHash;
  }

  public void setExposureTime(final Double exposureTime) {
    this.exposureTime = exposureTime;
  }

  public void setfNumber(final Double fNumber) {
    this.fNumber = fNumber;
  }

  public void setFocalLength(final Double focalLength) {
    this.focalLength = focalLength;
  }

  public void setIso(final Integer iso) {
    this.iso = iso;
  }

  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

  public void setMetaCaption(final String metaCaption) {
    this.metaCaption = metaCaption;
  }

  public void setMetaCaptionModified(final boolean metaCaptionModified) {
    this.metaCaptionModified = metaCaptionModified;
  }

  public void setMetaRating(final Integer metaRating) {
    this.metaRating = metaRating;
  }

  public void setMetaRatingModified(final boolean metaRatingModified) {
    this.metaRatingModified = metaRatingModified;
  }

  public void setOriginalSize(final long originalSize) {
    this.originalSize = originalSize;
  }

  public void setThumbnailSize(final long thumbnailSize) {
    this.thumbnailSize = thumbnailSize;
  }

}
