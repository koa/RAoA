package ch.bergturbenthal.raoa.server.spring.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.bergturbenthal.raoa.data.api.Album;
import ch.bergturbenthal.raoa.data.api.ImageResult;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.ImportFileRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.AlbumDataHandler;

@RestController
@RequestMapping("/albums")
public class AlbumController implements Album {
	private final class AbumDetailConverter implements Function<AlbumDataHandler, AlbumDetail> {
		private final String albumid;
		private final int count;
		private final int start;

		private AbumDetailConverter(final String albumid, final int start, final int count) {
			this.albumid = albumid;
			this.start = start;
			this.count = count;
		}

		@Override
		public AlbumDetail apply(final AlbumDataHandler albumData) {
			final AlbumDetail ret = new AlbumDetail(albumid, albumData.getAlbumName());
			ret.setTitle(albumData.getAlbumTitle().orElse(null));
			ret.setTitleEntry(albumData.getAlbumTitleEntry().orElse(null));
			ret.setCommitCount(albumData.getCommitCount());
			ret.setLastModified(albumData.getLastModified().map(instant -> Date.from(instant)).orElse(null));
			ret.getClients().addAll(albumData.getClients());
			ret.setAutoAddDate(albumData.getAutoAddDates().stream().map(instant -> Date.from(instant)).collect(Collectors.toList()));
			final Collection<AlbumImageEntry> images = ret.getImages();
			albumData.listImages().stream().skip(start).limit(count).map(image -> {
				final AlbumImageEntry imageEntry = new AlbumImageEntry();
				imageEntry.setId(image.getId());
				imageEntry.setName(image.getName());
				imageEntry.setVideo(image.isVideo().orElse(Boolean.FALSE));
				imageEntry.setCaptureDate(image.getCaptureDate().map(instant -> Date.from(instant)).orElse(null));
				imageEntry.setOriginalFileSize(image.getOriginalFileSize().orElse(-1l));
				imageEntry.setCameraMake(image.getCameraMake().orElse(null));
				imageEntry.setCameraModel(image.getCameraModel().orElse(null));
				imageEntry.setCaption(image.getCaption().orElse(null));
				imageEntry.setExposureTime(image.getExposureTime().orElse(null));
				imageEntry.setFocalLength(image.getFocalLength().orElse(null));
				imageEntry.setIso(image.getIso().orElse(null));
				imageEntry.setKeywords(image.getKeywords());
				imageEntry.setFNumber(image.getFNumber().orElse(null));
				return imageEntry;
			}).forEach(entry -> images.add(entry));

			return ret;
		}
	}

	private final class AlbumEntryConverter implements Function<AlbumDataHandler, AlbumEntry> {
		private final String albumid;

		private AlbumEntryConverter(final String albumid) {
			this.albumid = albumid;
		}

		@Override
		public AlbumEntry apply(final AlbumDataHandler albumData) {
			final AlbumEntry albumEntry = new AlbumEntry();
			albumEntry.setId(albumid);
			albumEntry.setCommitCount(albumData.getCommitCount());
			albumEntry.setLastModified(albumData.getLastModified().map(instant -> Date.from(instant)).orElse(null));
			albumEntry.setName(albumData.getAlbumName());
			albumEntry.setTitle(albumData.getAlbumTitle().orElse(null));
			return albumEntry;
		}

	}

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
		return albumAccess.getAlbumData(album).map(new AlbumEntryConverter(album)).orElseThrow(() -> new RuntimeException("Cannot take created repository"));

	}

	@RequestMapping(value = "{albumid}", method = RequestMethod.GET)
	public ResponseEntity<AlbumDetail> getListAlbumContent(	@PathVariable("albumid") final String albumid,
																													@RequestParam(name = "pageSize", defaultValue = "50000") final int pageSize,
																													@RequestParam(name = "pageNo", defaultValue = "0") final int pageNr) {
		return albumAccess.getAlbumData(albumid)
											.map(new AbumDetailConverter(albumid, pageSize * pageNr, pageSize))
											.map(c -> ResponseEntity.ok(c))
											.orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));

	}

	@Override
	public void importFile(final ImportFileRequest request) {
		// TODO Auto-generated method stub

	}

	@Override
	public AlbumDetail listAlbumContent(final String albumid) {
		return albumAccess.getAlbumData(albumid).map(new AbumDetailConverter(albumid, 0, Integer.MAX_VALUE)).orElse(null);
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
