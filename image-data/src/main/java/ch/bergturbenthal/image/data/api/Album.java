package ch.bergturbenthal.image.data.api;

import java.io.IOException;
import java.util.Date;

import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;

public interface Album {

  AlbumDetail listAlbumContent(String albumid);

  AlbumList listAlbums();

  ImageResult readImage(final String albumId, final String imageId, final int width, final int height, Date ifModifiedSince) throws IOException;

  void registerClient(String albumId, String clientId);

  void unRegisterClient(String albumId, String clientId);

}
