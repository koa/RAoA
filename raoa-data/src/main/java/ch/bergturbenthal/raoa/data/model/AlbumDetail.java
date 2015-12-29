package ch.bergturbenthal.raoa.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlbumDetail extends AlbumEntry {
	private Collection<Date> autoAddDate;
	private final Collection<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();
	private final Collection<String> interestingClients = new ArrayList<String>();
	private long repositorySize;
	private String title;
	private String titleEntry;

	public AlbumDetail(final String id, final String name) {
		super(id, name);
	}
}
