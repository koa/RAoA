package ch.bergturbenthal.raoa.server.metadata;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import lombok.Cleanup;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

public class MetadataFactoryImpl implements MetadataFactory {
	MetadataHolder readmetaDataFromFile(final File file) {
		try {
			try {
				// first try metadatareader from drewnoakes
				final Metadata metadata = ImageMetadataReader.readMetadata(file);
				if (metadata != null) {
					return new MetadataWrapper(metadata);
				}
			} catch (final ImageProcessingException ex) {
				// file format seems to be not supported
			}
			if (file.getName().toLowerCase().endsWith(".mp4")) {

				@Cleanup
				final IsoFile isoFile = new IsoFile(file.getAbsolutePath());
				final MovieBox movieBox = isoFile.getMovieBox();
				if (movieBox == null) {
					return null;
				}
				final MovieHeaderBox movieHeaderBox = movieBox.getMovieHeaderBox();
				if (movieHeaderBox == null) {
					return null;
				}

				final Date creationDate = movieHeaderBox.getCreationTime();

				return new MetadataHolder() {

					@Override
					public void fill(final AlbumEntryData loadedMetaData) {
						loadedMetaData.setCameraDate(creationDate);
					}

					@Override
					public Date readCameraDate() {
						return creationDate;
					}

					@Override
					public Date readCreateDate() {
						return creationDate;
					}

					@Override
					public Date readGpsDate() {
						return null;
					}
				};
			}
			return null;
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read metadata from " + file, e);
		}
	}

}
