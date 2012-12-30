package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.util.Collection;
import java.util.Date;

import ch.bergturbenthal.image.provider.model.dto.AlbumDto;

public interface AlbumConnection {
  Collection<String> connectedServers();

  AlbumDto getAlbumDetail();

  String getCommId();

  void readThumbnail(final String fileId, final File tempFile, final File targetFile);

  Date lastModified();
}
