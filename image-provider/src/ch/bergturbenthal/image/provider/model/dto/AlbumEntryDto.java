package ch.bergturbenthal.image.provider.model.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.provider.Client;
import ch.bergturbenthal.image.provider.map.CursorField;
import ch.bergturbenthal.image.provider.util.ParcelUtil;

public class AlbumEntryDto implements Parcelable {
  private String cameraMake;
  private String cameraModel;
  private String caption;
  private Date captureDate;
  private String commId;
  private String editableMetadataHash;
  private AlbumEntryType entryType;
  private Double exposureTime;
  private String fileName;
  private Double fNumber;
  private Double focalLength;
  private Integer iso;
  private final Collection<String> keywords = new LinkedHashSet<String>();
  private Date lastModified;
  private long originalFileSize;
  private Integer rating;
  private Long thumbnailSize;

  public static final Parcelable.Creator<AlbumEntryDto> CREATOR = new Parcelable.Creator<AlbumEntryDto>() {

    @Override
    public AlbumEntryDto createFromParcel(final Parcel source) {
      final AlbumEntryDto ret = new AlbumEntryDto();

      final ClassLoader classLoader = ret.getClass().getClassLoader();
      ret.setCameraMake(source.readString());
      ret.setCameraModel(source.readString());
      ret.setCaption(source.readString());
      ret.setCaptureDate(ParcelUtil.readDate(source));
      ret.setCommId(source.readString());
      ret.setEditableMetadataHash(source.readString());
      ret.setEntryType(ParcelUtil.readEnum(source, AlbumEntryType.class));
      ret.setExposureTime((Double) source.readValue(classLoader));
      ret.setFileName(source.readString());
      ret.setfNumber((Double) source.readValue(classLoader));
      ret.setFocalLength((Double) source.readValue(classLoader));
      ret.setIso((Integer) source.readValue(classLoader));
      final ArrayList<String> k = new ArrayList<String>();
      source.readList(k, classLoader);
      ret.getKeywords().addAll(k);
      ret.setLastModified(ParcelUtil.readDate(source));
      ret.setOriginalFileSize(source.readLong());
      ret.setRating((Integer) source.readValue(classLoader));
      ret.setThumbnailSize((Long) source.readValue(classLoader));
      return ret;
    }

    @Override
    public AlbumEntryDto[] newArray(final int size) {
      return new AlbumEntryDto[size];
    }
  };

  @Override
  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
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

  /**
   * Returns the albumName.
   * 
   * @return the albumName
   */
  @CursorField(Client.AlbumEntry.NAME)
  public String getFileName() {
    return fileName;
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

  /**
   * Sets the albumName.
   * 
   * @param albumName
   *          the albumName to set
   */
  public void setFileName(final String albumName) {
    this.fileName = albumName;
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

  @Override
  public void writeToParcel(final Parcel dest, final int flags) {
    dest.writeString(cameraMake);
    dest.writeString(cameraModel);
    dest.writeString(caption);
    ParcelUtil.writeDate(captureDate, dest);
    dest.writeString(commId);
    dest.writeString(editableMetadataHash);
    ParcelUtil.writeEnum(entryType, dest);
    dest.writeValue(exposureTime);
    dest.writeString(fileName);
    dest.writeValue(fNumber);
    dest.writeValue(focalLength);
    dest.writeValue(iso);
    dest.writeList(new ArrayList<String>(keywords));
    ParcelUtil.writeDate(lastModified, dest);
    dest.writeLong(originalFileSize);
    dest.writeValue(rating);
    dest.writeValue(thumbnailSize);
  }

}
