package ch.bergturbenthal.raoa.server.spring.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.json.AlbumMetadata;
import ch.bergturbenthal.raoa.json.InstanceData;

public interface AlbumAccess {
	public interface AlbumDataHandler {
		String getAlbumName();

		Optional<String> getAlbumTitle();

		Optional<String> getAlbumTitleEntry();

		Collection<Instant> getAutoAddDates();

		Collection<String> getClients();

		int getCommitCount();

		Optional<Instant> getLastModified();

		Collection<ImageDataHandler> listImages();
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

		String getName();

		Optional<Long> getOriginalFileSize();

		Optional<Boolean> isVideo();
	}

	boolean addAutoaddBeginDate(String album, Instant instant);

	String createAlbum(String[] pathComps);

	Optional<AlbumDataHandler> getAlbumData(String albumid);

	AlbumMetadata getAlbumMetadata(String albumId);

	Collection<Instant> getAutoaddBeginDates(final String album);

	InstanceData getInstanceData();

	List<String> listAlbums();

	AlbumEntry takeAlbumEntry(String album);
}
