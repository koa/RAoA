package ch.bergturbenthal.raoa.server.metadata;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;

public class Mp4MetadataUtil {
	public static Date findCreationDateOf(final File videoFile) {
		try {
			final MovieBox movieBox = MP4Util.parseMovie(videoFile);
			final TrakBox[] tracks = movieBox.getTracks();
			for (final TrakBox trakBox : tracks) {
				final TrackHeaderBox trackHeader = trakBox.getTrackHeader();
				if (trackHeader == null) {
					continue;
				}
				return new Date(trackHeader.getCreated());
			}
			return null;
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read Metadata from " + videoFile, e);
		}
	}
}
