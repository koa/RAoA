package ch.bergturbenthal.image.server;

import java.io.IOException;

import lombok.Cleanup;

import org.springframework.core.io.ClassPathResource;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;

public class Mp4MetadataParser {

	public static void main(final String[] args) throws IOException {
		final ClassPathResource resource = new ClassPathResource("DCIM/MVI_0002.MP4");
		@Cleanup
		final IsoFile isoFile = new IsoFile(resource.getFile().getAbsolutePath());
		final MovieBox movieBox = isoFile.getMovieBox();
		final MovieHeaderBox movieHeaderBox = movieBox.getMovieHeaderBox();

		System.out.println("Create date: " + movieHeaderBox.getCreationTime());
	}
}
