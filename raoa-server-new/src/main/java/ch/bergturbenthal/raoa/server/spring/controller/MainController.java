package ch.bergturbenthal.raoa.server.spring.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.bergturbenthal.raoa.json.AlbumMetadata;
import ch.bergturbenthal.raoa.json.InstanceData;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;

@RestController
public class MainController {
	@Autowired
	private AlbumAccess albumAccess;

	@RequestMapping("album/{albumId}")
	public AlbumMetadata getAlbums(@PathVariable("albumId") final String albumId) {
		return albumAccess.getAlbumMetadata(albumId);
	}

	@RequestMapping("/instance")
	public InstanceData getInstanceData() {
		return albumAccess.getInstanceData();
	}

	@RequestMapping("album")
	public List<String> listAlbums() {
		return albumAccess.listAlbums();
	}
}
