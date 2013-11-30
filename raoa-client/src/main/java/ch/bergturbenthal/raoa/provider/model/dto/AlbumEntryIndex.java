/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

public class AlbumEntryIndex {
	private final String albumEntryId;
	private final AlbumIndex albumIndex;

	public AlbumEntryIndex(final AlbumIndex album, final String albumEntryId) {
		this.albumIndex = album;
		this.albumEntryId = albumEntryId;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final AlbumEntryIndex other = (AlbumEntryIndex) obj;
		if (albumIndex == null) {
			if (other.albumIndex != null)
				return false;
		} else if (!albumIndex.equals(other.albumIndex))
			return false;
		if (albumEntryId == null) {
			if (other.albumEntryId != null)
				return false;
		} else if (!albumEntryId.equals(other.albumEntryId))
			return false;
		return true;
	}

	/**
	 * Returns the albumEntryId.
	 * 
	 * @return the albumEntryId
	 */
	public String getAlbumEntryId() {
		return albumEntryId;
	}

	public AlbumIndex getAlbumIndex() {
		return albumIndex;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((albumIndex == null) ? 0 : albumIndex.hashCode());
		result = prime * result + ((albumEntryId == null) ? 0 : albumEntryId.hashCode());
		return result;
	}

}