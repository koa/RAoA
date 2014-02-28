package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AlbumDto {
	private String albumTitle;
	private String albumTitleEntry;
	private Collection<Date> autoAddDate;
	private final Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();
	private Date lastModified;

	public String getAlbumTitle() {
		return albumTitle;
	}

	public String getAlbumTitleEntry() {
		return albumTitleEntry;
	}

	public Collection<Date> getAutoAddDate() {
		return autoAddDate;
	}

	public Map<String, AlbumEntryDto> getEntries() {
		return entries;
	}

	public Date getLastModified() {
		return lastModified;
	}

	public void setAlbumTitle(final String albumTitle) {
		this.albumTitle = albumTitle;
	}

	public void setAlbumTitleEntry(final String albumTitleEntry) {
		this.albumTitleEntry = albumTitleEntry;
	}

	public void setAutoAddDate(final Collection<Date> autoAddDate) {
		this.autoAddDate = autoAddDate;
	}

	public void setLastModified(final Date lastModified) {
		this.lastModified = lastModified;
	}

}
