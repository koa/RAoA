package ch.bergturbenthal.raoa.server.spring.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ch.bergturbenthal.raoa.data.api.Album;
import ch.bergturbenthal.raoa.data.api.ImageResult;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.ImportFileRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;

@RestController
@RequestMapping("/albums")
public class AlbumController implements Album {
	@Autowired
	private AlbumAccess albumAccess;

	@Override
	@RequestMapping(method = RequestMethod.POST)
	public AlbumEntry createAlbum(@RequestBody final CreateAlbumRequest request) {
		final String album = albumAccess.createAlbum(request.getPathComps());
		final Date autoAddDate = request.getAutoAddDate();
		if (autoAddDate != null) {
			albumAccess.addAutoaddBeginDate(album, autoAddDate.toInstant());
		}
		return albumAccess.takeAlbumEntry(album);
	}

	@Override
	public void importFile(final ImportFileRequest request) {
		// TODO Auto-generated method stub

	}

	@Override
	public AlbumDetail listAlbumContent(final String albumid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@RequestMapping(method = RequestMethod.GET)
	public AlbumList listAlbums() {
		final AlbumList ret = new AlbumList();
		final Collection<AlbumEntry> entryList = ret.getAlbumNames();
		for (final String entry : albumAccess.listAlbums()) {
			final AlbumEntry albumEntry = albumAccess.takeAlbumEntry(entry);
			if (albumEntry != null) {
				entryList.add(albumEntry);
			}
		}
		return ret;
	}

	@Override
	public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerClient(final String albumId, final String clientId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setAutoAddDate(final String albumId, final Date autoAddDate) {
		// TODO Auto-generated method stub

	}

	@Override
	public void unRegisterClient(final String albumId, final String clientId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateMetadata(final String albumId, final UpdateMetadataRequest request) {
		// TODO Auto-generated method stub

	}

}
