package ch.bergturbenthal.raoa.server.spring.model;

import java.util.Collection;
import java.util.Date;

import lombok.Data;

@Data
public class AlbumEntryMetadata {
	private String cameraMake;
	private String cameraModel;
	private String cameraSerial;
	private String caption;
	private Date captureDate;
	private Double exposureTime;
	private Double fNumber;
	private Double focalLength;
	private Date gpsDate;
	private Integer iso;
	private boolean isVideo;
	private Collection<String> keywords;
	private long originalFileSize;
	private Integer rating;
	private Long thumbnailFileSize;

	public void appendKeywords(final Collection<String> keywords) {
		if (this.keywords == null) {
			this.keywords = keywords;
			return;
		}
		if (keywords == null) {
			return;
		}
		this.keywords.addAll(keywords);

	}

}
