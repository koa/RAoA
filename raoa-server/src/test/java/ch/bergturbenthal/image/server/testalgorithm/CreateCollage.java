package ch.bergturbenthal.image.server.testalgorithm;

import java.io.File;
import java.io.IOException;
import java.util.List;

import ch.bergturbenthal.raoa.server.collage.Collage;
import ch.bergturbenthal.raoa.server.collage.Image;
import ch.bergturbenthal.raoa.server.collage.Rectangle;

public class CreateCollage {
	public static void main(final String[] args) throws IOException {
		final List<Image> originalImages = Collage.readImagesFromDirectory(new File("/tmp/collage"));
		final Rectangle collage = Collage.createMosaic(originalImages, 84 * 50, 58 * 50);
		Collage.writeImage(collage, new File("/tmp/collage-2.jpg"));
	}
}
