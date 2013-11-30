package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.map.CursorField;
import ch.bergturbenthal.raoa.provider.util.ParcelUtil;

public class AlbumEntryDto implements Parcelable, Comparable<AlbumEntryDto> {
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

	private static int dateCompare(final Date date1, final Date date2) {
		return (date1 == null ? new Date(0) : date1).compareTo(date2 == null ? new Date(0) : date2);
	}

	public static AlbumEntryDto fromServer(final AlbumImageEntry entry) {
		final AlbumEntryDto dtoEntry = new AlbumEntryDto();
		dtoEntry.setEntryType(entry.isVideo() ? AlbumEntryType.VIDEO : AlbumEntryType.IMAGE);
		dtoEntry.setLastModified(entry.getLastModified());
		dtoEntry.setCaptureDate(entry.getCaptureDate());
		dtoEntry.setCommId(entry.getId());
		dtoEntry.setFileName(entry.getName());
		dtoEntry.setOriginalFileSize(entry.getOriginalFileSize());
		dtoEntry.setThumbnailSize(entry.getThumbnailFileSize());

		dtoEntry.setCameraMake(entry.getCameraMake());
		dtoEntry.setCameraModel(entry.getCameraModel());
		dtoEntry.setCaption(entry.getCaption());
		dtoEntry.setEditableMetadataHash(entry.getEditableMetadataHash());
		dtoEntry.setExposureTime(entry.getExposureTime());
		dtoEntry.setfNumber(entry.getFNumber());
		dtoEntry.setFocalLength(entry.getFocalLength());
		dtoEntry.setIso(entry.getIso());
		if (entry.getKeywords() != null) {
			dtoEntry.getKeywords().addAll(entry.getKeywords());
		}
		dtoEntry.setRating(entry.getRating());
		return dtoEntry;
	}

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

	@Override
	public int compareTo(final AlbumEntryDto another) {
		final AlbumEntryDto lhs = this;
		final AlbumEntryDto rhs = another;

		final int dateDifference = dateCompare(lhs.getCaptureDate(), rhs.getCaptureDate());
		if (dateDifference != 0)
			return dateDifference;
		final int fileNameOrder = lhs.getFileName().compareTo(rhs.getFileName());
		if (fileNameOrder != 0)
			return fileNameOrder;
		return lhs.getCommId().compareTo(rhs.getCommId());
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final AlbumEntryDto other = (AlbumEntryDto) obj;
		if (cameraMake == null) {
			if (other.cameraMake != null)
				return false;
		} else if (!cameraMake.equals(other.cameraMake))
			return false;
		if (cameraModel == null) {
			if (other.cameraModel != null)
				return false;
		} else if (!cameraModel.equals(other.cameraModel))
			return false;
		if (caption == null) {
			if (other.caption != null)
				return false;
		} else if (!caption.equals(other.caption))
			return false;
		if (captureDate == null) {
			if (other.captureDate != null)
				return false;
		} else if (!captureDate.equals(other.captureDate))
			return false;
		if (commId == null) {
			if (other.commId != null)
				return false;
		} else if (!commId.equals(other.commId))
			return false;
		if (editableMetadataHash == null) {
			if (other.editableMetadataHash != null)
				return false;
		} else if (!editableMetadataHash.equals(other.editableMetadataHash))
			return false;
		if (entryType != other.entryType)
			return false;
		if (exposureTime == null) {
			if (other.exposureTime != null)
				return false;
		} else if (!exposureTime.equals(other.exposureTime))
			return false;
		if (fNumber == null) {
			if (other.fNumber != null)
				return false;
		} else if (!fNumber.equals(other.fNumber))
			return false;
		if (fileName == null) {
			if (other.fileName != null)
				return false;
		} else if (!fileName.equals(other.fileName))
			return false;
		if (focalLength == null) {
			if (other.focalLength != null)
				return false;
		} else if (!focalLength.equals(other.focalLength))
			return false;
		if (iso == null) {
			if (other.iso != null)
				return false;
		} else if (!iso.equals(other.iso))
			return false;
		if (keywords == null) {
			if (other.keywords != null)
				return false;
		} else if (!keywords.equals(other.keywords))
			return false;
		if (lastModified == null) {
			if (other.lastModified != null)
				return false;
		} else if (!lastModified.equals(other.lastModified))
			return false;
		if (originalFileSize != other.originalFileSize)
			return false;
		if (rating == null) {
			if (other.rating != null)
				return false;
		} else if (!rating.equals(other.rating))
			return false;
		if (thumbnailSize == null) {
			if (other.thumbnailSize != null)
				return false;
		} else if (!thumbnailSize.equals(other.thumbnailSize))
			return false;
		return true;
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

	@CursorField(Client.AlbumEntry.ENTRY_TYPE)
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cameraMake == null) ? 0 : cameraMake.hashCode());
		result = prime * result + ((cameraModel == null) ? 0 : cameraModel.hashCode());
		result = prime * result + ((caption == null) ? 0 : caption.hashCode());
		result = prime * result + ((captureDate == null) ? 0 : captureDate.hashCode());
		result = prime * result + ((commId == null) ? 0 : commId.hashCode());
		result = prime * result + ((editableMetadataHash == null) ? 0 : editableMetadataHash.hashCode());
		result = prime * result + ((entryType == null) ? 0 : entryType.hashCode());
		result = prime * result + ((exposureTime == null) ? 0 : exposureTime.hashCode());
		result = prime * result + ((fNumber == null) ? 0 : fNumber.hashCode());
		result = prime * result + ((fileName == null) ? 0 : fileName.hashCode());
		result = prime * result + ((focalLength == null) ? 0 : focalLength.hashCode());
		result = prime * result + ((iso == null) ? 0 : iso.hashCode());
		result = prime * result + ((keywords == null) ? 0 : keywords.hashCode());
		result = prime * result + ((lastModified == null) ? 0 : lastModified.hashCode());
		result = prime * result + (int) (originalFileSize ^ (originalFileSize >>> 32));
		result = prime * result + ((rating == null) ? 0 : rating.hashCode());
		result = prime * result + ((thumbnailSize == null) ? 0 : thumbnailSize.hashCode());
		return result;
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
	public String toString() {
		return "AlbumEntryDto [cameraMake=" + cameraMake
						+ ", cameraModel="
						+ cameraModel
						+ ", caption="
						+ caption
						+ ", captureDate="
						+ captureDate
						+ ", commId="
						+ commId
						+ ", editableMetadataHash="
						+ editableMetadataHash
						+ ", entryType="
						+ entryType
						+ ", exposureTime="
						+ exposureTime
						+ ", fileName="
						+ fileName
						+ ", fNumber="
						+ fNumber
						+ ", focalLength="
						+ focalLength
						+ ", iso="
						+ iso
						+ ", keywords="
						+ keywords
						+ ", lastModified="
						+ lastModified
						+ ", originalFileSize="
						+ originalFileSize
						+ ", rating="
						+ rating
						+ ", thumbnailSize="
						+ thumbnailSize
						+ "]";
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
