package ch.bergturbenthal.image.provider.model.dto;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import ch.bergturbenthal.image.provider.model.AlbumEntryType;

public class AlbumEntryDto {
  private Date captureDate;
  private String commId;
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

  public String getCameraMake() {
    return cameraMake;
  }

  public String getCameraModel() {
    return cameraModel;
  }

  public String getCaption() {
    return caption;
  }

  /**
   * Returns the captureDate.
   * 
   * @return the captureDate
   */
  public Date getCaptureDate() {
    return captureDate;
  }

  public String getCommId() {
    return commId;
  }

  public String getEditableMetadataHash() {
    return editableMetadataHash;
  }

  public AlbumEntryType getEntryType() {
    return entryType;
  }

  public Double getExposureTime() {
    return exposureTime;
  }

  public Double getfNumber() {
    return fNumber;
  }

  public Double getFocalLength() {
    return focalLength;
  }

  public Integer getIso() {
    return iso;
  }

  public Collection<String> getKeywords() {
    return keywords;
  }

  public Date getLastModified() {
    return lastModified;
  }

  public long getOriginalFileSize() {
    return originalFileSize;
  }

  public Integer getRating() {
    return rating;
  }

  public Long getThumbnailSize() {
    return thumbnailSize;
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
