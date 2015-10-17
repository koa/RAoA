package ch.bergturbenthal.raoa.server.spring.model;

import java.util.Collection;
import java.util.Date;

import lombok.Data;
import lombok.experimental.Builder;

@Data
@Builder
public class AlbumEntryMetadata {
	private String cameraMake;
	private String cameraModel;
	private String caption;
	private Date captureDate;
	private Double exposureTime;

	private Double fNumber;
	private Double focalLength;
	private Integer iso;
	private boolean isVideo;
	private Collection<String> keywords;
	private long originalFileSize;
	private Integer rating;
	private Long thumbnailFileSize;

}
