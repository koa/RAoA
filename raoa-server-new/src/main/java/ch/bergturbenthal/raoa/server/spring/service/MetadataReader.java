package ch.bergturbenthal.raoa.server.spring.service;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectLoader;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;

public interface MetadataReader {
	void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadata.AlbumEntryMetadataBuilder metadata) throws IOException;
}
