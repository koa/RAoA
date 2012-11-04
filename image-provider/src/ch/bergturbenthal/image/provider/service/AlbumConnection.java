package ch.bergturbenthal.image.provider.service;

import java.util.Collection;

import ch.bergturbenthal.image.data.model.AlbumDetail;

public interface AlbumConnection {
  Collection<String> connectedServers();

  AlbumDetail getAlbumDetail();
}
