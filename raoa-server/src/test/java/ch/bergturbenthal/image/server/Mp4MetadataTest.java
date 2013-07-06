package ch.bergturbenthal.image.server;

import java.io.IOException;

import lombok.Cleanup;

import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.ClassPathResource;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.ContainerBox;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.TrackBox;
import com.googlecode.mp4parser.authoring.DateHelper;

public class Mp4MetadataTest {
	public static void main(final String[] args) throws IOException {
		final ClassPathResource resource = new ClassPathResource("DCIM/MVI_0002.MP4");
		System.out.println("Readable: " + resource.isReadable());
		@Cleanup
		final IsoFile isoFile2 = new IsoFile(resource.getFile());
		dumpBox(isoFile2, 0);
		System.out.println("------------------");
		final MovieBox moov = isoFile2.getMovieBox();
		for (final Box b : moov.getBoxes()) {
			if (b instanceof TrackBox) {
				final TrackBox trackBox = (TrackBox) b;
				final MediaBox mediaBox = trackBox.getMediaBox();
				if (mediaBox != null) {
					System.out.println("Creation Time: " + DateHelper.convert(mediaBox.getMediaHeaderBox().getCreationTime()));
				}
			}
			System.out.println(b);
		}
	}

	private static void dumpBox(final ContainerBox container, final int level) {
		for (final Box box : container.getBoxes()) {
			System.out.println(StringUtils.repeat("  ", level) + box);
			if (box instanceof ContainerBox) {
				dumpBox((ContainerBox) box, level + 1);
			}
		}
	}
}
