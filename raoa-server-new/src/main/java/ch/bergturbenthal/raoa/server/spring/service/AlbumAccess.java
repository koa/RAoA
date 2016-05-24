package ch.bergturbenthal.raoa.server.spring.service;

import java.util.List;

import ch.bergturbenthal.raoa.json.AlbumMetadata;
import ch.bergturbenthal.raoa.json.InstanceData;

public interface AlbumAccess {
	AlbumMetadata getAlbumMetadata(String albumId);

	InstanceData getInstanceData();

	List<String> listAlbums();
}
