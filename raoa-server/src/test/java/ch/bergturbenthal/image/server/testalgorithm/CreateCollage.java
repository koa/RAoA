package ch.bergturbenthal.image.server.testalgorithm;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import ch.bergturbenthal.raoa.server.collage.Collage;
import ch.bergturbenthal.raoa.server.collage.Image;
import ch.bergturbenthal.raoa.server.collage.Rectangle;

public class CreateCollage {
	public static void main(final String[] args) throws IOException {
		final int collageCount = 6;
		final List<Image> originalImages = Collage.readImagesFromDirectory(new File("/tmp/bea/prepared"));
		Collections.shuffle(originalImages);
		int currentPos = 0;
		int remainingCount = collageCount;
		final File outDir = new File("/tmp/bea/collage");
		outDir.mkdirs();
		while (currentPos < originalImages.size()) {
			final int currentCollageCount = (originalImages.size() - currentPos) / remainingCount--;
			final int endPos = currentPos + currentCollageCount;
			final List<Image> currentCollageList = originalImages.subList(currentPos, endPos);
			for (int i = 0; i < 5; i++) {
				final Rectangle collage = Collage.createMosaic(currentCollageList, 4200, (2900));
				Collage.writeImage(collage, new File(outDir, "collage-" + remainingCount + "-" + i + ".jpg"));
			}
			currentPos = endPos;
		}
	}
}
