package ch.bergturbenthal.raoa.server.spring.service;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.lib.ObjectLoader;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;

public interface MetadataReader {
	Collection<String> metadataFileOf(final String filename);

	void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata metadata) throws IOException;
}
