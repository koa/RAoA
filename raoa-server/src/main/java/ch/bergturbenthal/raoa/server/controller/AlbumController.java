package ch.bergturbenthal.raoa.server.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.data.api.ImageResult;
import ch.bergturbenthal.raoa.data.api.ImageResult.ResultCode;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.Util;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.model.AlbumMetadata;
import ch.bergturbenthal.raoa.server.watcher.DirectoryNotificationService;

@Controller
@RequestMapping("/albums")
public class AlbumController implements ch.bergturbenthal.raoa.data.api.Album {
	private static Logger logger = LoggerFactory.getLogger(AlbumController.class);
	@Autowired
	private AlbumAccess albumAccess;

	@Autowired
	private DirectoryNotificationService directoryNotificationService;

	@Override
	@RequestMapping(method = RequestMethod.POST)
	public @ResponseBody
	AlbumEntry createAlbum(@RequestBody final CreateAlbumRequest request) {
		final Album album = albumAccess.createAlbum(request.getPathComps());
		final Date autoAddDate = request.getAutoAddDate();
		if (autoAddDate != null) {
			album.setAutoAddBeginDate(autoAddDate);
		}
		return makeAlbumEntry(Util.encodeStringForUrl(album.getName()), album);
	}

	@RequestMapping(value = "import", method = RequestMethod.GET)
	public void importDirectory(@RequestParam("path") final String path, final HttpServletResponse response) throws IOException, InterruptedException, ExecutionException {
		directoryNotificationService.notifyDirectory(new File(path)).get();
		response.getWriter().println("Import finished");
	}

	@Override
	public AlbumDetail listAlbumContent(final String albumid) {
		final Album album = albumAccess.listAlbums().get(albumid);
		if (album == null) {
			return null;
		}
		final AlbumMetadata albumMetadata = album.getAlbumMetadata();
		final AlbumDetail ret = new AlbumDetail();
		ret.setId(albumid);
		ret.getClients().addAll(albumAccess.clientsPerAlbum(albumid));
		ret.setAutoAddDate(album.getAutoAddBeginDate());
		ret.setLastModified(album.getLastModified());
		ret.setRepositorySize(album.getRepositorySize());
		ret.setTitle(albumMetadata.getAlbumTitle());
		final String titleEntry = albumMetadata.getTitleEntry();
		final Map<String, AlbumImage> images = album.listImages();
		for (final Entry<String, AlbumImage> albumImageEntry : images.entrySet()) {
			final AlbumImageEntry entry = new AlbumImageEntry();
			final AlbumImage albumImage = albumImageEntry.getValue();
			if (titleEntry != null && albumImage.getName().equals(titleEntry)) {
				ret.setTitleEntry(albumImageEntry.getKey());
			}
			entry.setId(albumImageEntry.getKey());
			fillAlbumImageEntry(albumImage, entry);
			ret.getImages().add(entry);
		}
		return ret;
	}

	@RequestMapping(value = "{albumid}", method = RequestMethod.GET)
	public @ResponseBody
	AlbumDetail listAlbumContent(@PathVariable("albumid") final String albumid, final HttpServletResponse response) {
		final AlbumDetail content = listAlbumContent(albumid);
		if (content == null) {
			response.setStatus(404);
		}
		return content;
	}

	@Override
	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody
	AlbumList listAlbums() {
		final AlbumList albumList = new AlbumList();
		final Collection<AlbumEntry> albumNames = albumList.getAlbumNames();
		for (final Entry<String, Album> entry : albumAccess.listAlbums().entrySet()) {
			final AlbumEntry albumEntry = makeAlbumEntry(entry.getKey(), entry.getValue());
			albumNames.add(albumEntry);
		}
		return albumList;
	}

	@Override
	public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException {
		final Album album = albumAccess.getAlbum(albumId);
		if (album == null) {
			return null;
		}
		final AlbumImage image = album.getImage(imageId);
		if (image == null) {
			return null;
		}
		final File thumbnailImage = image.getThumbnail(true);
		if (thumbnailImage != null) {
			return makeImageResult(thumbnailImage, image, ifModifiedSince);
		}
		return null;
	}

	@RequestMapping(value = "{albumId}/image/{imageId}.jpg", method = RequestMethod.GET)
	public void readImage(@PathVariable("albumId") final String albumId,
												@PathVariable("imageId") final String imageId,
												final HttpServletRequest request,
												final HttpServletResponse response) throws IOException {
		final long modifiedTime = request.getDateHeader("If-Modified-Since");
		final ImageResult foundImage = readImage(albumId, imageId, modifiedTime > 0 ? new Date(modifiedTime) : null);
		if (foundImage == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if (foundImage.getStatus() == ResultCode.NOT_MODIFIED) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}
		if (foundImage.getStatus() == ResultCode.TRY_LATER) {
			response.setStatus(HttpServletResponse.SC_ACCEPTED);
			return;
		}
		response.setContentType(foundImage.getMimeType());
		final Date created = foundImage.getCreated();
		if (created != null) {
			response.setDateHeader("created-at", created.getTime());
		}
		final InputStream inputStream = foundImage.getDataStream();
		try {
			response.setDateHeader("last-modified", foundImage.getLastModified().getTime());
			final ServletOutputStream outputStream = response.getOutputStream();
			try {
				final byte[] buffer = new byte[8192];
				while (true) {
					final int read = inputStream.read(buffer);
					if (read < 0) {
						break;
					}
					outputStream.write(buffer, 0, read);
				}
			} finally {
				outputStream.close();
			}
		} finally {
			inputStream.close();
		}
	}

	@Override
	@RequestMapping(value = "{albumId}/registerClient", method = RequestMethod.GET)
	public void registerClient(@PathVariable("albumId") final String albumId, @RequestParam("clientId") final String clientId) {
		albumAccess.registerClient(albumId, clientId);
	}

	@RequestMapping(value = "{albumId}/registerClient", method = RequestMethod.PUT)
	public void registerClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
		registerClient(albumId, clientId);
	}

	@Override
	public void setAutoAddDate(final String albumId, final Date autoAddDate) {
		final Album album = albumAccess.getAlbum(albumId);
		if (album == null) {
			return;
		}
		album.setAutoAddBeginDate(autoAddDate);
	}

	@RequestMapping(value = "{albumId}/setAutoAddDate", method = RequestMethod.PUT)
	public void setAutoAddDate(@PathVariable("albumId") final String albumId, @RequestBody final Date autoAddDate, final HttpServletResponse response) {
		setAutoAddDate(albumId, autoAddDate);
	}

	@Override
	@RequestMapping(value = "{albumId}/unRegisterClient", method = RequestMethod.GET)
	public void unRegisterClient(@PathVariable("albumId") final String albumId, @RequestParam("clientId") final String clientId) {
		albumAccess.unRegisterClient(albumId, clientId);
	}

	@RequestMapping(value = "{albumId}/unRegisterClient", method = RequestMethod.PUT)
	public void unRegisterClient(@PathVariable("albumId") final String albumId, @RequestBody final String clientId, final HttpServletResponse response) {
		unRegisterClient(albumId, clientId);
	}

	@Override
	public void updateMetadata(final String albumId, final UpdateMetadataRequest request) {
		albumAccess.updateMetadata(albumId, request.getMutationEntries());
	}

	@RequestMapping(value = "{albumId}/updateMeta", method = RequestMethod.PUT)
	public void updateMetadata(@PathVariable("albumId") final String albumId, @RequestBody final UpdateMetadataRequest request, final HttpServletResponse response) {
		updateMetadata(albumId, request);
	}

	private void fillAlbumImageEntry(final AlbumImage albumImage, final AlbumImageEntry entry) {
		entry.setName(albumImage.getName());
		entry.setVideo(albumImage.isVideo());
		entry.setLastModified(albumImage.lastModified());
		entry.setOriginalFileSize(albumImage.getOriginalFileSize());
		final File thumbnail = albumImage.getThumbnail(true);
		if (thumbnail != null) {
			entry.setThumbnailFileSize(thumbnail.length());
		}
		try {
			final AlbumEntryData albumEntryData = albumImage.getAlbumEntryData();

			entry.setCaptureDate(albumImage.captureDate());
			entry.setCameraMake(albumEntryData.getCameraMake());
			entry.setCameraModel(albumEntryData.getCameraModel());
			entry.setCaption(albumEntryData.getCaption());
			entry.setEditableMetadataHash(albumEntryData.getEditableMetadataHash());
			entry.setExposureTime(albumEntryData.getExposureTime());
			entry.setFNumber(albumEntryData.getFNumber());
			entry.setFocalLength(albumEntryData.getFocalLength());
			entry.setIso(albumEntryData.getIso());
			entry.setKeywords(albumEntryData.getKeywords());
			entry.setRating(albumEntryData.getRating());
		} catch (final RuntimeException ex) {
			logger.warn("cannot read metadata from image " + albumImage.getName(), ex);
		}
	}

	private AlbumEntry makeAlbumEntry(final String id, final Album album) {
		final AlbumEntry albumEntry = new AlbumEntry(id, album.getName());
		albumEntry.setLastModified(album.getLastModified());
		albumEntry.getClients().addAll(albumAccess.clientsPerAlbum(id));
		return albumEntry;
	}

	private ImageResult makeImageResult(final File sourceFile, final AlbumImage image, final Date ifModifiedSince) {
		final Date lastModified = new Date(sourceFile.lastModified());
		if (ifModifiedSince == null || ifModifiedSince.before(lastModified)) {
			return ImageResult.makeModifiedResult(lastModified, image.captureDate(), new ImageResult.StreamSource() {

				@Override
				public InputStream getInputStream() throws IOException {
					return new FileInputStream(sourceFile);
				}
			}, image.isVideo() ? "video/mp4" : "image/jpeg");
		} else {
			return ImageResult.makeNotModifiedResult();
		}
	}
}
