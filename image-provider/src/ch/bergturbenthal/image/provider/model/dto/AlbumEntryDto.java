package ch.bergturbenthal.image.provider.model.dto;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.CursorField;

public class AlbumEntryDto {
  private Date captureDate;
  private String commId;
  private String fileName;
  private AlbumEntryType entryType;
  private Date lastModified;
  private long originalFileSize;
  private Long thumbnailSize;
  private String cameraMake;
  private String cameraModel;
  private String caption;
  private String editableMetadataHash;
  private Double exposureTime;
  private Double fNumber;
  private Double focalLength;
  private Integer iso;
  private final Collection<String> keywords = new LinkedHashSet<String>();
  private Integer rating;

  /**
   * Returns the albumName.
   * 
   * @return the albumName
   */
  @CursorField(Client.AlbumEntry.NAME)
  public String getFileName() {
    return fileName;
  }

  @CursorField(Client.AlbumEntry.CAMERA_MAKE)
  public String getCameraMake() {
    return cameraMake;
  }

  @CursorField(Client.AlbumEntry.CAMERA_MODEL)
  public String getCameraModel() {
    return cameraModel;
  }

  @CursorField(Client.AlbumEntry.META_CAPTION)
  public String getCaption() {
    return caption;
  }

  /**
   * Returns the captureDate.
   * 
   * @return the captureDate
   */
  @CursorField(Client.AlbumEntry.CAPTURE_DATE)
  public Date getCaptureDate() {
    return captureDate;
  }

  @CursorField(Client.AlbumEntry.ID)
  public String getCommId() {
    return commId;
  }

  public String getEditableMetadataHash() {
    return editableMetadataHash;
  }

  public AlbumEntryType getEntryType() {
    return entryType;
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

  @CursorField(Client.AlbumEntry.ISO)
  public Integer getIso() {
    return iso;
  }

  public Collection<String> getKeywords() {
    return keywords;
  }

  @CursorField(Client.AlbumEntry.LAST_MODIFIED)
  public Date getLastModified() {
    return lastModified;
  }

  @CursorField(Client.AlbumEntry.ORIGINAL_SIZE)
  public long getOriginalFileSize() {
    return originalFileSize;
  }

  @CursorField(Client.AlbumEntry.META_RATING)
  public Integer getRating() {
    return rating;
  }

  @CursorField(Client.AlbumEntry.THUMBNAIL_SIZE)
  public Long getThumbnailSize() {
    return thumbnailSize;
  }

  /**
   * Sets the albumName.
   * 
   * @param albumName
   *          the albumName to set
   */
  public void setFileName(final String albumName) {
    this.fileName = albumName;
  }

  public void setCameraMake(final String cameraMake) {
    this.cameraMake = cameraMake;
  }

  public void setCameraModel(final String cameraModel) {
    this.cameraModel = cameraModel;
  }

  public void setCaption(final String caption) {
    this.caption = caption;
  }

  /**
   * Sets the captureDate.
   * 
   * @param captureDate
   *          the captureDate to set
   */
  public void setCaptureDate(final Date captureDate) {
    this.captureDate = captureDate;
  }

  public void setCommId(final String commId) {
    this.commId = commId;
  }

  public void setEditableMetadataHash(final String editableMetadataHash) {
    this.editableMetadataHash = editableMetadataHash;
  }

  public void setEntryType(final AlbumEntryType entryType) {
    this.entryType = entryType;
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

  public void setOriginalFileSize(final long originalFileSize) {
    this.originalFileSize = originalFileSize;
  }

  public void setRating(final Integer rating) {
    this.rating = rating;
  }

  public void setThumbnailSize(final Long thumbnailSize) {
    this.thumbnailSize = thumbnailSize;
  }

}
