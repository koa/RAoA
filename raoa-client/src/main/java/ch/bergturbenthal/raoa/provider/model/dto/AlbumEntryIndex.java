/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

public class AlbumEntryIndex extends AlbumIndex {
	private final String albumEntryId;

	public AlbumEntryIndex(final String archiveName, final String albumId, final String albumEntryId) {
		super(archiveName, albumId);
		this.albumEntryId = albumEntryId;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		final AlbumEntryIndex other = (AlbumEntryIndex) obj;
		if (albumEntryId == null) {
			if (other.albumEntryId != null)
				return false;
		} else if (!albumEntryId.equals(other.albumEntryId))
			return false;
		else if (!super.equals(obj))
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((albumEntryId == null) ? 0 : albumEntryId.hashCode());
		result = prime * result + super.hashCode();
		return result;
	}

}