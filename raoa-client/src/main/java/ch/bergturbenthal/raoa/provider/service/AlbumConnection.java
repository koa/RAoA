package ch.bergturbenthal.raoa.provider.service;

import java.io.File;
import java.util.Collection;
import java.util.Date;

import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.provider.model.dto.AlbumDto;

public interface AlbumConnection {
	Collection<String> connectedServers();

	AlbumDto getAlbumDetail();

	String getCommId();

	boolean readThumbnail(final String fileId, final File tempFile, final File targetFile);

	Date lastModified();

	void updateMetadata(final Collection<Mutation> updateEntries);
}
