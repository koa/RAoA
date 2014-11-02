package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Builder;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE, suppressConstructorProperties = true)
@Builder
public class AlbumDto implements Serializable {
	private String albumTitle;
	private String albumTitleEntry;
	private Collection<Date> autoAddDate;
	private final Map<String, AlbumEntryDto> entries;
	private Date lastModified;
}
