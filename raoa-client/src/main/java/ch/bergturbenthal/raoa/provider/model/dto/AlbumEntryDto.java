package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.provider.Client;
import ch.bergturbenthal.raoa.provider.map.CursorField;

@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE, suppressConstructorProperties = true)
public class AlbumEntryDto implements Comparable<AlbumEntryDto>, Serializable {

	private static int dateCompare(final Date date1, final Date date2) {
		return (date1 == null ? new Date(0) : date1).compareTo(date2 == null ? new Date(0) : date2);
	}

	public static AlbumEntryDto fromServer(final AlbumImageEntry entry) {
		final AlbumEntryDtoBuilder builder = builder();
		builder.entryType(entry.isVideo() ? AlbumEntryType.VIDEO : AlbumEntryType.IMAGE);
		builder.lastModified(entry.getLastModified());
		builder.captureDate(entry.getCaptureDate());
		builder.commId(entry.getId());
		builder.fileName(entry.getName());
		builder.originalFileSize(entry.getOriginalFileSize());
		builder.thumbnailSize(entry.getThumbnailFileSize());

		builder.cameraMake(entry.getCameraMake());
		builder.cameraModel(entry.getCameraModel());
		builder.caption(entry.getCaption());
		builder.editableMetadataHash(entry.getEditableMetadataHash());
		builder.exposureTime(entry.getExposureTime());
		builder.fNumber(entry.getFNumber());
		builder.focalLength(entry.getFocalLength());
		builder.iso(entry.getIso());
		if (entry.getKeywords() != null) {
			builder.keywords(Collections.unmodifiableCollection(new ArrayList<String>(entry.getKeywords())));
		} else {
			builder.keywords(Collections.<String> emptyList());
		}

		builder.rating(entry.getRating());
		return builder.build();
	}

	@CursorField(Client.AlbumEntry.CAMERA_MAKE)
	private String cameraMake;
	@CursorField(Client.AlbumEntry.CAMERA_MODEL)
	private String cameraModel;
	@CursorField(Client.AlbumEntry.META_CAPTION)
	private String caption;
	@CursorField(Client.AlbumEntry.CAPTURE_DATE)
	private Date captureDate;
	@CursorField(Client.AlbumEntry.ID)
	private String commId;
	private String editableMetadataHash;
	@CursorField(Client.AlbumEntry.ENTRY_TYPE)
	private AlbumEntryType entryType;
	@CursorField(Client.AlbumEntry.EXPOSURE_TIME)
	private Double exposureTime;
	@CursorField(Client.AlbumEntry.NAME)
	private String fileName;
	@CursorField(Client.AlbumEntry.F_NUMBER)
	private Double fNumber;
	@CursorField(Client.AlbumEntry.FOCAL_LENGTH)
	private Double focalLength;
	@CursorField(Client.AlbumEntry.ISO)
	private Integer iso;
	@NonNull
	private Collection<String> keywords;
	@CursorField(Client.AlbumEntry.LAST_MODIFIED)
	private Date lastModified;
	@CursorField(Client.AlbumEntry.ORIGINAL_SIZE)
	private long originalFileSize;
	@CursorField(Client.AlbumEntry.META_RATING)
	private Integer rating;
	@CursorField(Client.AlbumEntry.THUMBNAIL_SIZE)
	private Long thumbnailSize;

	@Override
	public int compareTo(final AlbumEntryDto another) {
		final AlbumEntryDto lhs = this;
		final AlbumEntryDto rhs = another;

		final int dateDifference = dateCompare(lhs.getCaptureDate(), rhs.getCaptureDate());
		if (dateDifference != 0) {
			return dateDifference;
		}
		final int fileNameOrder = lhs.getFileName().compareTo(rhs.getFileName());
		if (fileNameOrder != 0) {
			return fileNameOrder;
		}
		return lhs.getCommId().compareTo(rhs.getCommId());
	}

}
