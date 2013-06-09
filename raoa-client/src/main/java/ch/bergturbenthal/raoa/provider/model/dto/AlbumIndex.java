/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

public class AlbumIndex {
	protected Integer cachedHash = null;
	private final String albumId;
	private final String archiveName;

	public AlbumIndex(final String archiveName, final String albumId) {
		this.archiveName = archiveName;
		this.albumId = albumId;
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
		final AlbumIndex other = (AlbumIndex) obj;
		if (cachedHash != null && other.cachedHash != null && !cachedHash.equals(other.cachedHash)) {
			return false;
		}
		if (albumId == null) {
			if (other.albumId != null) {
				return false;
			}
		} else if (!albumId.equals(other.albumId)) {
			return false;
		}
		if (archiveName == null) {
			if (other.archiveName != null) {
				return false;
			}
		} else if (!archiveName.equals(other.archiveName)) {
			return false;
		}
		return true;
	}

	/**
	 * Returns the albumId.
	 * 
	 * @return the albumId
	 */
	public String getAlbumId() {
		return albumId;
	}

	/**
	 * Returns the archiveName.
	 * 
	 * @return the archiveName
	 */
	public String getArchiveName() {
		return archiveName;
	}

	@Override
	public int hashCode() {
		if (cachedHash != null) {
			return cachedHash.intValue();
		}
		final int prime = 31;
		int result = 1;
		result = prime * result + ((albumId == null) ? 0 : albumId.hashCode());
		result = prime * result + ((archiveName == null) ? 0 : archiveName.hashCode());
		cachedHash = Integer.valueOf(result);
		return result;
	}

}