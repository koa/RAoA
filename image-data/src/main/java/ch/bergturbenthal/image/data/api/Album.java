package ch.bergturbenthal.image.data.api;

import java.io.File;

import ch.bergturbenthal.image.data.model.AlbumDetail;
import ch.bergturbenthal.image.data.model.AlbumList;

public interface Album {

  AlbumDetail listAlbumContent(String albumid);

  AlbumList listAlbums();

  File readImage(final String albumId, final String imageId, final int width, final int height);

}
