/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;

import lombok.NonNull;
import lombok.Value;

@Value
public class AlbumEntryIndex implements Comparable<AlbumEntryIndex>, Serializable {
	@NonNull
	private final String albumEntryId;
	private final AlbumIndex albumIndex;

	public AlbumEntryIndex(final AlbumIndex album, final String albumEntryId) {
		this.albumIndex = album;
		this.albumEntryId = albumEntryId;
	}

	@Override
	public int compareTo(final AlbumEntryIndex another) {
		final int albumCompare = albumIndex.compareTo(another.getAlbumIndex());
		if (albumCompare != 0) {
			return albumCompare;
		}
		return albumEntryId.compareTo(another.getAlbumEntryId());
	}
}