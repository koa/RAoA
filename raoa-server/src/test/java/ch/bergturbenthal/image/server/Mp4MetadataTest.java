package ch.bergturbenthal.image.server;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieHeaderBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import ch.bergturbenthal.raoa.server.metadata.Mp4MetadataUtil;

public class Mp4MetadataTest {
	public static void main(final String[] args) throws IOException {
		final ClassPathResource resource = new ClassPathResource("DCIM/MVI_0002.MP4");
		System.out.println("Readable: " + resource.isReadable());
		final MovieBox movieBox = MP4Util.parseMovie(resource.getFile());
		dumpBoxes(movieBox, 0);
		final MovieHeaderBox mediaInfoBox = Box.findFirst(movieBox, MovieHeaderBox.class, MovieHeaderBox.fourcc());
		System.out.println(mediaInfoBox);
		final TrakBox[] tracks = movieBox.getTracks();
		for (final TrakBox trakBox : tracks) {
			final TrackHeaderBox header2 = trakBox.getTrackHeader();
			System.out.println(new Date(header2.getCreated()));
		}

	}

	private static void dumpBoxes(final NodeBox movieBox, final int level) {
		for (final Box box : movieBox.getBoxes()) {
			System.out.println(StringUtils.repeat("  ", level) + box.getFourcc() + "  " + box.getClass().getSimpleName());
			if (box instanceof NodeBox) {
				dumpBoxes((NodeBox) box, level + 1);
			}
		}
	}

	@Test
	public void testReadCreationDate() throws IOException {
		final ClassPathResource resource = new ClassPathResource("DCIM/MVI_0002.MP4");
		final Date date = Mp4MetadataUtil.findCreationDateOf(resource.getFile());
		Assert.assertEquals(1373097210000l, date.getTime());
	}
}
