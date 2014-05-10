package ch.bergturbenthal.raoa.server.metadata;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import lombok.Cleanup;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;

public class Mp4MetadataUtil {
	public static Date findCreationDateOf(final File videoFile) {
		try {
			@Cleanup
			final IsoFile isoFile = new IsoFile(videoFile.getAbsolutePath());
			final MovieBox movieBox = isoFile.getMovieBox();
			if (movieBox == null) {
				return null;
			}
			final MovieHeaderBox movieHeaderBox = movieBox.getMovieHeaderBox();
			if (movieHeaderBox == null) {
				return null;
			}
			return movieHeaderBox.getCreationTime();
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read Metadata from " + videoFile, e);
		}
	}
}
