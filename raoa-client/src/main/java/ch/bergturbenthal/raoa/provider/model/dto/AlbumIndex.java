/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;

@Value
@Builder
@AllArgsConstructor(suppressConstructorProperties = true)
public class AlbumIndex implements Comparable<AlbumIndex>, Serializable {
	@NonNull
	private final String albumId;
	@NonNull
	private final String archiveName;

	@Override
	public int compareTo(final AlbumIndex another) {
		final int archivCompare = archiveName.compareTo(another.archiveName);
		if (archivCompare != 0) {
			return archivCompare;
		}
		return albumId.compareTo(another.albumId);
	}
}