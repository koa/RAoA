/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import lombok.experimental.Wither;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.map.CursorField;

/**
 * TODO: add type comment.
 *
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE, suppressConstructorProperties = true)
@Builder
public class AlbumMeta implements Serializable {

	/**
	 *
	 */
	private static final long	   serialVersionUID	= 1585226909297518367L;
	@CursorField(Client.Album.ALBUM_CAPTURE_DATE)
	private Date	               albumDate;
	@CursorField(Client.Album.ID)
	private String	             albumId;
	private String	             albumTitle;
	@CursorField(Client.Album.ARCHIVE_NAME)
	private String	             archiveName;
	@NonNull
	private Collection<Date>	   autoAddDate;
	@CursorField(Client.Album.ENTRY_COUNT)
	private int	                 entryCount;
	@NonNull
	private Map<String, Integer>	keywordCounts;
	private Date	               lastModified;
	@Wither
	@CursorField(Client.Album.NAME)
	private String	             name;
	@CursorField(Client.Album.ORIGINALS_SIZE)
	private long	               originalsSize;
	@CursorField(Client.Album.REPOSITORY_SIZE)
	private long	               repositorySize;
	private String	             thumbnailId;
	@CursorField(Client.Album.THUMBNAILS_SIZE)
	private long	               thumbnailSize;

}
