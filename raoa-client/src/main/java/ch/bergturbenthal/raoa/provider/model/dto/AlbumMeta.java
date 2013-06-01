/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.map.CursorField;
import ch.bergturbenthal.raoa.provider.util.ParcelUtil;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumMeta implements Parcelable {
	public static Parcelable.Creator<AlbumMeta> CREATOR = new Parcelable.Creator<AlbumMeta>() {

		@Override
		public AlbumMeta createFromParcel(final Parcel source) {
			final AlbumMeta ret = new AlbumMeta();
			ret.setLastModified(ParcelUtil.readDate(source));
			ret.setName(source.readString());
			ret.setAutoAddDate(ParcelUtil.readDate(source));
			ret.setThumbnailId(source.readString());
			ret.setAlbumDate(ParcelUtil.readDate(source));
			ret.setArchiveName(source.readString());
			ret.setAlbumId(source.readString());
			ret.setEntryCount(source.readInt());
			ret.setThumbnailSize(source.readLong());
			ret.setRepositorySize(source.readLong());
			ret.setOriginalsSize(source.readLong());
			ret.setAlbumTitle(source.readString());
			final HashMap<String, Integer> map = new HashMap<String, Integer>();
			source.readMap(map, ret.getClass().getClassLoader());
			ret.getKeywordCounts().putAll(map);
			return ret;
		}

		@Override
		public AlbumMeta[] newArray(final int size) {
			return new AlbumMeta[size];
		}
	};

	private Date albumDate;
	private String albumId;
	private String albumTitle;
	private String archiveName;
	private Date autoAddDate;
	private int entryCount;
	private final Map<String, Integer> keywordCounts = new HashMap<String, Integer>();
	private Date lastModified;
	private String name;
	private long originalsSize;
	private long repositorySize;
	private String thumbnailId;
	private long thumbnailSize;

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final AlbumMeta other = (AlbumMeta) obj;
		if (albumDate == null) {
			if (other.albumDate != null) {
				return false;
			}
		} else if (!albumDate.equals(other.albumDate)) {
			return false;
		}
		if (albumId == null) {
			if (other.albumId != null) {
				return false;
			}
		} else if (!albumId.equals(other.albumId)) {
			return false;
		}
		if (albumTitle == null) {
			if (other.albumTitle != null) {
				return false;
			}
		} else if (!albumTitle.equals(other.albumTitle)) {
			return false;
		}
		if (archiveName == null) {
			if (other.archiveName != null) {
				return false;
			}
		} else if (!archiveName.equals(other.archiveName)) {
			return false;
		}
		if (autoAddDate == null) {
			if (other.autoAddDate != null) {
				return false;
			}
		} else if (!autoAddDate.equals(other.autoAddDate)) {
			return false;
		}
		if (entryCount != other.entryCount) {
			return false;
		}
		if (keywordCounts == null) {
			if (other.keywordCounts != null) {
				return false;
			}
		} else if (!keywordCounts.equals(other.keywordCounts)) {
			return false;
		}
		if (lastModified == null) {
			if (other.lastModified != null) {
				return false;
			}
		} else if (!lastModified.equals(other.lastModified)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (originalsSize != other.originalsSize) {
			return false;
		}
		if (repositorySize != other.repositorySize) {
			return false;
		}
		if (thumbnailId == null) {
			if (other.thumbnailId != null) {
				return false;
			}
		} else if (!thumbnailId.equals(other.thumbnailId)) {
			return false;
		}
		if (thumbnailSize != other.thumbnailSize) {
			return false;
		}
		return true;
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

	public String getAlbumTitle() {
		return albumTitle;
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
	 * Returns the keywordCounts.
	 * 
	 * @return the keywordCounts
	 */
	public Map<String, Integer> getKeywordCounts() {
		return keywordCounts;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((albumDate == null) ? 0 : albumDate.hashCode());
		result = prime * result + ((albumId == null) ? 0 : albumId.hashCode());
		result = prime * result + ((albumTitle == null) ? 0 : albumTitle.hashCode());
		result = prime * result + ((archiveName == null) ? 0 : archiveName.hashCode());
		result = prime * result + ((autoAddDate == null) ? 0 : autoAddDate.hashCode());
		result = prime * result + entryCount;
		result = prime * result + ((keywordCounts == null) ? 0 : keywordCounts.hashCode());
		result = prime * result + ((lastModified == null) ? 0 : lastModified.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (int) (originalsSize ^ (originalsSize >>> 32));
		result = prime * result + (int) (repositorySize ^ (repositorySize >>> 32));
		result = prime * result + ((thumbnailId == null) ? 0 : thumbnailId.hashCode());
		result = prime * result + (int) (thumbnailSize ^ (thumbnailSize >>> 32));
		return result;
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

	public void setAlbumTitle(final String albumTitle) {
		this.albumTitle = albumTitle;
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
	public String toString() {
		return "AlbumMeta [albumDate=" + albumDate
						+ ", albumId="
						+ albumId
						+ ", albumTitle="
						+ albumTitle
						+ ", archiveName="
						+ archiveName
						+ ", autoAddDate="
						+ autoAddDate
						+ ", entryCount="
						+ entryCount
						+ ", keywordCounts="
						+ keywordCounts
						+ ", lastModified="
						+ lastModified
						+ ", name="
						+ name
						+ ", originalsSize="
						+ originalsSize
						+ ", repositorySize="
						+ repositorySize
						+ ", thumbnailId="
						+ thumbnailId
						+ ", thumbnailSize="
						+ thumbnailSize
						+ "]";
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		ParcelUtil.writeDate(lastModified, dest);
		dest.writeString(name);
		ParcelUtil.writeDate(autoAddDate, dest);
		dest.writeString(thumbnailId);
		ParcelUtil.writeDate(albumDate, dest);
		dest.writeString(archiveName);
		dest.writeString(albumId);
		dest.writeInt(entryCount);
		dest.writeLong(thumbnailSize);
		dest.writeLong(repositorySize);
		dest.writeLong(originalsSize);
		dest.writeString(albumTitle);
		dest.writeMap(keywordCounts);
	}

}
