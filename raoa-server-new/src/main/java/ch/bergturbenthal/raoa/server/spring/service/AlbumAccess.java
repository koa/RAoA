package ch.bergturbenthal.raoa.server.spring.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import ch.bergturbenthal.raoa.json.AlbumMetadata;
import ch.bergturbenthal.raoa.json.InstanceData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AlbumAccess {
	public interface AlbumDataHandler {
		String getAlbumName();

		Optional<String> getAlbumTitle();

		Optional<String> getAlbumTitleEntry();

		Collection<Instant> getAutoAddDates();

		Optional<Instant> getCaptureTime();

		Collection<String> getClients();

		int getCommitCount();

		Optional<Instant> getLastModified();

		Collection<ImageDataHandler> listImages();

	}

	public interface FileContent {
		InputStream takeInputStream() throws IOException;
	}

	public interface ImageDataHandler {
		Optional<String> getCameraMake();

		Optional<String> getCameraModel();

		Optional<String> getCaption();

		Optional<Instant> getCaptureDate();

		Optional<Double> getExposureTime();

		Optional<Double> getFNumber();

		Optional<Double> getFocalLength();

		String getId();

		Optional<Integer> getIso();

		Collection<String> getKeywords();

		Optional<Instant> getLastModified();

		Optional<String> getName();

		Optional<Long> getOriginalFileSize();

		Optional<Boolean> isVideo();

		Optional<FileContent> mobileData() throws IOException;

		Optional<FileContent> thumnbailData() throws IOException;
	}

	boolean addAutoaddBeginDate(String album, Instant instant);

	Mono<String> createAlbum(String[] pathComps);

	Optional<AlbumDataHandler> getAlbumData(String albumid);

	AlbumMetadata getAlbumMetadata(String albumId);

	Collection<Instant> getAutoaddBeginDates(final String album);

	InstanceData getInstanceData();

	Flux<String> listAlbums();

	Optional<ImageDataHandler> takeImageById(String albumId, String imageId);

}
