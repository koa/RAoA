package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;

@Service
@Order(0)
public class ExifMetadataReader extends AbstractDrewMetadataReader {
	@Override
	public Collection<String> metadataFileOf(final String filename) {
		final String lowerFilename = filename.toLowerCase();
		if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || lowerFilename.endsWith(".nef") || lowerFilename.endsWith(".png")) {
			return Collections.singleton(filename);
		}
		return Collections.emptySet();
	}

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata metadata) throws IOException {
		try {
			final Metadata readMetadata = ImageMetadataReader.readMetadata(objectLoader.openStream());

			final Double exposureTime = readExposureTime(readMetadata);
			if (exposureTime != null) {
				metadata.setExposureTime(exposureTime);
			}
			final Double fNumber = readFNumber(readMetadata);
			if (fNumber != null) {
				metadata.setFNumber(fNumber);
			}
			final Date cameraDate = readCameraDate(readMetadata);
			if (cameraDate != null) {
				metadata.setCaptureDate(cameraDate);
			}
			final Date gpsDate = readGpsDate(readMetadata);
			if (gpsDate != null) {
				metadata.setGpsDate(gpsDate);
			}
			final Double focalLength = readFocalLength(readMetadata);
			if (focalLength != null) {
				metadata.setFocalLength(focalLength);
			}
			final Integer iso = readIso(readMetadata);
			if (iso != null) {
				metadata.setIso(iso);
			}
			final Collection<String> keywords = readKeywords(readMetadata);
			if (keywords != null && !keywords.isEmpty()) {
				metadata.appendKeywords(keywords);
			}
			final Integer rating = readRating(readMetadata);
			if (rating != null) {
				metadata.setRating(rating);
			}
			final String cameraMake = readCameraMake(readMetadata);
			if (cameraMake != null) {
				metadata.setCameraMake(cameraMake);
			}
			final String cameraModel = readCameraModel(readMetadata);
			if (cameraModel != null) {
				metadata.setCameraModel(cameraModel);
			}
			final String caption = readCaption(readMetadata);
			if (caption != null) {
				metadata.setCaption(caption);
			}
			final String cameraSerial = readCameraSerial(readMetadata);
			if (cameraSerial != null) {
				metadata.setCameraSerial(cameraSerial);
			}
		} catch (final ImageProcessingException e) {
			throw new RuntimeException("Error reading metdata", e);
		}
	}
}
