package ch.bergturbenthal.raoa.server.util;

import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.Map.Entry;

import ch.bergturbenthal.raoa.server.AlbumImage;

public class AlbumEntryComparator implements Comparator<Entry<String, AlbumImage>> {
	@Override
	public int compare(final Entry<String, AlbumImage> e1, final Entry<String, AlbumImage> e2) {
		final Date d1 = Optional.ofNullable(e1.getValue())
														.flatMap(v1 -> Optional.ofNullable(v1.getAlbumEntryData()))
														.flatMap(v2 -> Optional.ofNullable(v2.getCreationDate()))
														.orElse(new Date(0));
		final Date d2 = Optional.ofNullable(e2.getValue())
														.flatMap(v3 -> Optional.ofNullable(v3.getAlbumEntryData()))
														.flatMap(v4 -> Optional.ofNullable(v4.getCreationDate()))
														.orElse(new Date(0));
		return d1.compareTo(d2);
	}
}