package ch.bergturbenthal.raoa.server.spring.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
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
import org.springframework.web.context.request.async.DeferredResult;

import ch.bergturbenthal.raoa.data.api.ImageResult;
import ch.bergturbenthal.raoa.data.model.AlbumDetail;
import ch.bergturbenthal.raoa.data.model.AlbumEntry;
import ch.bergturbenthal.raoa.data.model.AlbumImageEntry;
import ch.bergturbenthal.raoa.data.model.AlbumList;
import ch.bergturbenthal.raoa.data.model.CreateAlbumRequest;
import ch.bergturbenthal.raoa.data.model.UpdateMetadataRequest;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess.AlbumDataHandler;
import reactor.core.Cancellation;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

@RestController
@RequestMapping("/albums")
public class AlbumController {
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
				imageEntry.setName(image.getName().orElse(null));
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

	@RequestMapping(method = RequestMethod.POST)
	public Mono<AlbumEntry> createAlbumObservable(@RequestBody final CreateAlbumRequest request) {
		final Date autoAddDate = request.getAutoAddDate();
		final Mono<String> createAlbum = albumAccess.createAlbum(request.getPathComps());
		final Mono<String> created = createAlbum.then((Function<String, Mono<String>>) album -> {
			if (autoAddDate != null) {
				albumAccess.addAutoaddBeginDate(album, autoAddDate.toInstant());
			}
			return Mono.just(album);
		});
		final Function<String, AlbumEntry> map = album -> albumAccess	.getAlbumData(album)
																																	.map(new AlbumEntryConverter(album))
																																	.orElseThrow(() -> new RuntimeException("Cannot take created repository"));
		return created.map(map);

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

	public AlbumDetail listAlbumContent(final String albumid) {
		return albumAccess.getAlbumData(albumid).map(new AbumDetailConverter(albumid, 0, Integer.MAX_VALUE)).orElse(null);
	}

	@RequestMapping(method = RequestMethod.GET)
	public AlbumList listAlbums() {
		final AlbumList ret = new AlbumList();
		final Collection<AlbumEntry> entryList = ret.getAlbumNames();
		for (final String entry : albumAccess.listAlbums()) {
			final Optional<AlbumEntry> albumEntry = albumAccess.getAlbumData(entry).map(new AlbumEntryConverter(entry));
			if (albumEntry.isPresent()) {
				entryList.add(albumEntry.get());
			}
		}
		return ret;
	}

	private <T> DeferredResult<T> mono2singleResult(final Mono<T> mono) {
		final DeferredResult<T> ret = new DeferredResult<>();
		final Cancellation cancellation = mono.subscribe(t -> ret.setResult(t), t -> ret.setErrorResult(t));
		ret.onTimeout(() -> {
			cancellation.dispose();
		});
		return ret;
	}

	private <T> DeferredResult<T> observable2singleResult(final Observable<T> observable) {
		final DeferredResult<T> ret = new DeferredResult<T>();
		final Subscription subscription = observable.subscribe(new Subscriber<T>() {

			@Override
			public void onCompleted() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onError(final Throwable e) {
				ret.setErrorResult(e);
			}

			@Override
			public void onNext(final T t) {
				ret.setResult(t);
			}
		});
		ret.onTimeout(() -> {
			subscription.unsubscribe();
		});
		return ret;
	}

	public ImageResult readImage(final String albumId, final String imageId, final Date ifModifiedSince) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public void registerClient(final String albumId, final String clientId) {

	}

	public void setAutoAddDate(final String albumId, final Date autoAddDate) {
		// TODO Auto-generated method stub

	}

	public void unRegisterClient(final String albumId, final String clientId) {
		// TODO Auto-generated method stub

	}

	public void updateMetadata(final String albumId, final UpdateMetadataRequest request) {
		// TODO Auto-generated method stub

	}

}
