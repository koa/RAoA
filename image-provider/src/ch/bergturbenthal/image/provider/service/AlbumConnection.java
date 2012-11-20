package ch.bergturbenthal.image.provider.service;

import java.io.File;
import java.util.Collection;

import ch.bergturbenthal.image.provider.model.dto.AlbumDto;
import ch.bergturbenthal.image.provider.model.dto.AlbumEntryDetailDto;

public interface AlbumConnection {
  Collection<String> connectedServers();

  AlbumDto getAlbumDetail();

  AlbumEntryDetailDto getAlbumEntryDetail(String filename);

  void readThumbnail(String filename, File tempFile, File targetFile);
}
