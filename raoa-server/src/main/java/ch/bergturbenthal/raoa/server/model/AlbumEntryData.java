package ch.bergturbenthal.raoa.server.model;

import java.awt.Dimension;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;

import ch.bergturbenthal.raoa.data.model.Location;
import ch.bergturbenthal.raoa.server.metadata.CameraOrientation;
import lombok.Data;

@Data
public class AlbumEntryData {
	private Date cameraDate;
	private String cameraMake;
	private String cameraModel;
	private String cameraSerial;
	private String caption;
	private Date creationDate;
	private String editableMetadataHash;
	private Double exposureTime;
	private Double fNumber;
	private Double focalLength;
	private Date gpsDate;
	private Integer iso;
	private Collection<String> keywords;
	private Date lastModifiedMetadata;
	private Location location;
	private Optional<CameraOrientation> orientation;
	private Optional<Dimension> originalDimension;
	private Integer rating;

	public Date estimateCreationDate() {
		if (creationDate != null) {
			return creationDate;
		}
		if (gpsDate != null) {
			return gpsDate;
		}
		return cameraDate;
	}
}
