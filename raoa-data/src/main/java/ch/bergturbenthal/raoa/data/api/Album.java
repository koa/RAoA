package ch.bergturbenthal.raoa.data.api;

import java.io.IOException;
import java.util.Date;

import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.ImportFileRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;

public interface Album {

	AlbumEntry createAlbum(final CreateAlbumRequest request);

	AlbumDetail listAlbumContent(final String albumid);

	AlbumList listAlbums();

	ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException;

	void registerClient(final String albumId, final String clientId);

	void setAutoAddDate(final String albumId, final Date autoAddDate);

	void unRegisterClient(final String albumId, final String clientId);

	void updateMetadata(final String albumId, final UpdateMetadataRequest request);

	void importFile(final ImportFileRequest request);

}
