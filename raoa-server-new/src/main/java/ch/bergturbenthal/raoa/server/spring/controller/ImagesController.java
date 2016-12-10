package ch.bergturbenthal.raoa.server.spring.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.FileContent;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.ImageDataHandler;
import lombok.Cleanup;

@RestController
@RequestMapping("/albums/{albumId}/image/{imageId}")
public class ImagesController {
	@Autowired
	private AlbumAccess albumAccess;

	@RequestMapping(path = "mobile.jpg", method = RequestMethod.GET)
	public void readMobileImage(@PathVariable("albumId") final String albumId,
															@PathVariable("imageId") final String imageId,
															final HttpServletRequest request,
															final HttpServletResponse response) throws IOException {

		final Optional<ImageDataHandler> imageById = albumAccess.takeImageById(albumId, imageId);
		if (!imageById.isPresent()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		final ImageDataHandler imageDataHandler = imageById.get();
		final Optional<FileContent> mobileData = imageDataHandler.mobileData();
		if (!mobileData.isPresent()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		@Cleanup
		final InputStream dataInputStreamOptinal = mobileData.get().takeInputStream();
		response.setContentType(imageDataHandler.isVideo().get() ? "video/mp4" : "image/jpeg");
		StreamUtils.copy(dataInputStreamOptinal, response.getOutputStream());

	}

	@RequestMapping(path = "thumbnail.jpg", method = RequestMethod.GET)
	public void readThumbnail(@PathVariable("albumId") final String albumId,
														@PathVariable("imageId") final String imageId,
														final HttpServletRequest request,
														final HttpServletResponse response) throws IOException {

		final Optional<ImageDataHandler> imageById = albumAccess.takeImageById(albumId, imageId);
		if (!imageById.isPresent()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		final ImageDataHandler imageDataHandler = imageById.get();
		final Optional<FileContent> mobileData = imageDataHandler.thumnbailData();
		if (!mobileData.isPresent()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		@Cleanup
		final InputStream dataInputStreamOptinal = mobileData.get().takeInputStream();
		response.setContentType(imageDataHandler.isVideo().get() ? "video/mp4" : "image/jpeg");
		StreamUtils.copy(dataInputStreamOptinal, response.getOutputStream());

	}

}
