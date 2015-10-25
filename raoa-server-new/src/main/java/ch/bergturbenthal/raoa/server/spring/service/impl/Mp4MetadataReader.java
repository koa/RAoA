package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.util.Path;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

@Service
@Order(0)
public class Mp4MetadataReader implements MetadataReader {

	@Override
	public Collection<String> metadataFileOf(final String filename) {
		if (filename.toLowerCase().endsWith(".mp4")) {
			return Collections.singleton(filename);
		}
		return null;
	}

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata metadata) throws IOException {

		final File tempFile = File.createTempFile("metadata-analyze", ".mp4");
		try {
			try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
				objectLoader.copyTo(outputStream);
			}
			final IsoFile isoFile = new IsoFile(new FileDataSourceImpl(tempFile));
			final MovieHeaderBox header = Path.getPath(isoFile, "moov[0]/mvhd[0]");
			metadata.setVideo(true);
			if (header != null) {
				metadata.setCaptureDate(header.getCreationTime());
			}
		} finally {
			tempFile.delete();
		}
	}
}
