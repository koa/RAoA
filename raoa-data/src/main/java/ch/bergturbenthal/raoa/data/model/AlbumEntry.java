package ch.bergturbenthal.raoa.data.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AlbumEntry {
	private final Collection<String> clients = new ArrayList<String>();
	private String id;
	private Date lastModified;
	private String name;
	private String title;

	public AlbumEntry(final String id, final String name) {
		this.id = id;
		this.name = name;
	}
}
