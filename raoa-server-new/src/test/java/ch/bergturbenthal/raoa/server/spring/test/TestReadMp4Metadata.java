package ch.bergturbenthal.raoa.server.spring.test;

import java.io.IOException;
import java.util.Date;

import lombok.Cleanup;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.googlecode.mp4parser.util.Path;

public class TestReadMp4Metadata {
	public static void dumpContainer(final Container container, final int depth) {
		for (final Box box : container.getBoxes()) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				sb.append("  ");
			}
			sb.append("- " + box.getType() + ", " + box);
			System.out.println(sb);
			if (box instanceof Container) {
				dumpContainer((Container) box, depth + 1);
			}
		}
	}

	public static void main(final String[] args) throws IOException {
		final long startTime = System.currentTimeMillis();
		@Cleanup
		final IsoFile isoFile = new IsoFile("/data/heap/data/photos/Pferde/Turniere/2015/SM Frauenfeld 2015/Andrea Rota Hindernis 1.mp4");
		final MovieHeaderBox header = Path.getPath(isoFile, "moov[0]/mvhd[0]");
		final Date creationTime = header.getCreationTime();
		final double duration = header.getDuration() * 1.0 / header.getTimescale();
		System.out.println("Created at " + creationTime + ", duration: " + duration);
		System.out.println((System.currentTimeMillis() - startTime) + " ms");
		dumpContainer(isoFile, 0);
		for (final Box box : isoFile.getBoxes()) {
			System.out.println(box);
		}
	}
}
