package ch.bergturbenthal.image.data.api;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;
import ch.bergturbenthal.image.data.model.MutationEntry;

public interface Album {

  String createAlbum(final String[] pathComps);

  AlbumDetail listAlbumContent(final String albumid);

  AlbumList listAlbums();

  ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException;

  void registerClient(final String albumId, final String clientId);

  void setAutoAddDate(final String albumId, final Date autoAddDate);

  void unRegisterClient(final String albumId, final String clientId);

  void updateMetadata(String albumId, final Collection<MutationEntry> updateEntries);
}
