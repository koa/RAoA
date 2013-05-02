package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AlbumDto {
	private Date autoAddDate;
	private final Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();
	private Date lastModified;

	public Date getAutoAddDate() {
		return autoAddDate;
	}

	public Map<String, AlbumEntryDto> getEntries() {
		return entries;
	}

	public Date getLastModified() {
		return lastModified;
	}

	public void setAutoAddDate(final Date autoAddDate) {
		this.autoAddDate = autoAddDate;
	}

	public void setLastModified(final Date lastModified) {
		this.lastModified = lastModified;
	}

}
