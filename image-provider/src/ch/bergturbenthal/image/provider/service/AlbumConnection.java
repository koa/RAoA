package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.util.Collection;

import ch.bergturbenthal.image.provider.model.dto.AlbumDto;

public interface AlbumConnection {
  Collection<String> connectedServers();

  AlbumDto getAlbumDetail();

  String getCommId();

  void readThumbnail(String fileId, File tempFile, File targetFile);
}
