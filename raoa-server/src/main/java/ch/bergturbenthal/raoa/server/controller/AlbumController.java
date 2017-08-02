package ch.bergturbenthal.raoa.server.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import ch.bergturbenthal.raoa.data.model.ImportFileRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.Util;
import ch.bergturbenthal.raoa.server.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.model.AlbumMetadata;
import ch.bergturbenthal.raoa.server.watcher.DirectoryNotificationService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/rest/albums")
public class AlbumController implements ch.bergturbenthal.raoa.data.api.Album {
	@Autowired
	private AlbumAccess albumAccess;

	private final Semaphore concurrentBuildThumbnailSemaphore = new Semaphore(10);

	@Autowired
	private DirectoryNotificationService directoryNotificationService;

	private void appendFile(final File originalFile, final ZipOutputStream zipStream) throws IOException {
		final byte[] buffer = new byte[4096];
		@Cleanup
		final FileInputStream inputStream = new FileInputStream(originalFile);
		while (true) {
			final int read = inputStream.read(buffer);
			if (read < 0) {
				break;
			}
			zipStream.write(buffer, 0, read);
		}
	}

	@Override
	@RequestMapping(method = RequestMethod.POST)
	public @ResponseBody AlbumEntry createAlbum(@RequestBody final CreateAlbumRequest request) {
		final Album album = albumAccess.createAlbum(request.getPathComps());
		final Date autoAddDate = request.getAutoAddDate();
		if (autoAddDate != null) {
			album.setAutoAddBeginDate(autoAddDate);
		}
		return makeAlbumEntry(Util.encodeStringForUrl(album.getName()), album);
	}

	@RequestMapping(value = "{albumid}/photos", method = RequestMethod.GET)
	public void downloadAlbumPhotos(@PathVariable("albumid") final String albumid, final HttpServletResponse response) throws IOException {

		final Album album = albumAccess.getAlbum(albumid);
		if (album == null) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return;
		}

		final Stream<File> fileStream = album	.listImages()
																					.values()
																					.stream()
																					.filter(i -> !i.isVideo())
																					.flatMap(image -> Stream.of(image.getOriginalFile(), image.getXmpSideFile()))
																					.filter(f -> f.exists());
		streamZipFile(fileStream, response, lastComp(album.getNameComps()) + "-photos.zip");
	}

	@RequestMapping(value = "{albumid}/photo-thumbnails", method = RequestMethod.GET)
	public void downloadAlbumPhotoThumbnails(@PathVariable("albumid") final String albumid, final HttpServletResponse response) throws IOException {

		final Album album = albumAccess.getAlbum(albumid);
		if (album == null) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return;
		}
		final Stream<File> fileStream = album	.listImages()
																					.values()
																					.stream()
																					.filter(i -> !i.isVideo())
																					.flatMap(image -> Stream.of(image.getThumbnail(), image.getXmpSideFile()))
																					.filter(f -> f.exists());
		streamZipFile(fileStream, response, lastComp(album.getNameComps()) + "-photos-thumbnails.zip");
	}

	@RequestMapping(value = "{albumid}/videos", method = RequestMethod.GET)
	public void downloadAlbumVideos(@PathVariable("albumid") final String albumid, final HttpServletResponse response) throws IOException {

		final Album album = albumAccess.getAlbum(albumid);
		if (album == null) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return;
		}
		final Stream<File> fileStream = album	.listImages()
																					.values()
																					.stream()
																					.filter(i -> i.isVideo())
																					.flatMap(image -> Stream.of(image.getOriginalFile(), image.getXmpSideFile()))
																					.filter(f -> f.exists());
		streamZipFile(fileStream, response, lastComp(album.getNameComps()) + "-videos.zip");
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
			log.warn("cannot read metadata from image " + albumImage.getName(), ex);
		}
	}

	@RequestMapping(value = "import", method = RequestMethod.GET)
	public void importDirectory(@RequestParam("path") final String path, final HttpServletResponse response) throws IOException, InterruptedException, ExecutionException {
		directoryNotificationService.notifyDirectory(new File(path)).get();
		response.getWriter().println("Import finished");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.data.api.Album#importFile(ch.bergturbenthal.raoa.data.model.ImportFileRequest)
	 */
	@Override
	@RequestMapping(value = "import", method = RequestMethod.PUT)
	@ResponseBody
	public void importFile(@RequestBody final ImportFileRequest request) {
		albumAccess.importFile(request.getFilename(), request.getData());
	}

	private String lastComp(final List<String> nameComps) {
		if (nameComps == null || nameComps.isEmpty()) {
			return null;
		}
		return nameComps.get(nameComps.size() - 1);
	}

	@Override
	public AlbumDetail listAlbumContent(final String albumid) {
		final Album album = albumAccess.listAlbums().get(albumid);
		if (album == null) {
			return null;
		}
		final AlbumMetadata albumMetadata = album.getAlbumMetadata();
		final AlbumDetail ret = new AlbumDetail(albumid, "");
		ret.getClients().addAll(albumAccess.clientsPerAlbum(albumid));
		ret.setAutoAddDate(album.getAutoAddBeginDate());
		ret.setLastModified(album.getLastModified());
		ret.setCommitCount(album.getCommitCount());
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
	public @ResponseBody AlbumDetail listAlbumContent(@PathVariable("albumid") final String albumid, final HttpServletResponse response) {
		final AlbumDetail content = listAlbumContent(albumid);
		if (content == null) {
			response.setStatus(404);
		}
		return content;
	}

	@Override
	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody AlbumList listAlbums() {
		final AlbumList albumList = new AlbumList();
		final Collection<AlbumEntry> albumNames = albumList.getAlbumNames();
		for (final Entry<String, Album> entry : albumAccess.listAlbums().entrySet()) {
			final AlbumEntry albumEntry = makeAlbumEntry(entry.getKey(), entry.getValue());
			albumNames.add(albumEntry);
		}
		return albumList;
	}

	private AlbumEntry makeAlbumEntry(final String id, final Album album) {
		final AlbumEntry albumEntry = new AlbumEntry(id, album.getName());
		albumEntry.setLastModified(album.getLastModified());
		albumEntry.setCommitCount(album.getCommitCount());
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
		if (concurrentBuildThumbnailSemaphore.tryAcquire()) {
			try {
				final File newThumbnailFile = image.getThumbnail(false);
				if (newThumbnailFile != null) {
					return makeImageResult(newThumbnailFile, image, ifModifiedSince);
				}
			} finally {
				concurrentBuildThumbnailSemaphore.release();
			}
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

	private void streamFiles(final Stream<File> stream, final OutputStream outputStream) throws IOException {
		@Cleanup
		final ZipOutputStream zipStream = new ZipOutputStream(outputStream);
		stream.forEach(file -> {
			try {
				final ZipEntry sideFileEntry = new ZipEntry(file.getName());
				sideFileEntry.setTime(file.lastModified());
				sideFileEntry.setSize(file.length());
				zipStream.putNextEntry(sideFileEntry);
				appendFile(file, zipStream);
			} catch (final IOException e) {
				throw new RuntimeException("Cannot stream " + file.getName(), e);
			}
		});
	}

	private void streamZipFile(final Stream<File> fileStream, final HttpServletResponse response, final String filename) throws IOException {
		response.setContentType("application/zip");
		response.addHeader("content-disposition", "attachment; filename=\"" + filename + "\"");
		streamFiles(fileStream, response.getOutputStream());
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
}
