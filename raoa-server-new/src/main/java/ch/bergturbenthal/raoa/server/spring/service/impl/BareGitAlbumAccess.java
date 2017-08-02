package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import ch.bergturbenthal.raoa.json.AlbumMetadata.AlbumMetadataBuilder;
import ch.bergturbenthal.raoa.json.InstanceData;
import ch.bergturbenthal.raoa.json.InstanceData.InstanceDataBuilder;
import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.model.AlbumCache;
import ch.bergturbenthal.raoa.server.spring.model.AlbumData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata;
import ch.bergturbenthal.raoa.server.spring.model.AlbumMetadata;
import ch.bergturbenthal.raoa.server.spring.model.ArchiveData;
import ch.bergturbenthal.raoa.server.spring.model.AttachementCache;
import ch.bergturbenthal.raoa.server.spring.model.BranchState;
import ch.bergturbenthal.raoa.server.spring.model.StorageData;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator.ObjectLoaderLookup;
import ch.bergturbenthal.raoa.server.spring.service.HashGenerator;
import ch.bergturbenthal.raoa.server.spring.util.CachingSupplier;
import ch.bergturbenthal.raoa.server.spring.util.FindGitDirWalker;
import lombok.Cleanup;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class BareGitAlbumAccess implements AlbumAccess {
	@Value
	private static class ArchiveMetadataHolder {
		private ArchiveData archiveData;
		private ObjectId lastCommit;
		private Instant lastLoadTime;
	}

	private static final class ByteArrayComparator implements Comparator<byte[]> {
		@Override
		public int compare(final byte[] o1, final byte[] o2) {
			if (o1 == o2) {
				return 0;
			}
			if (o1 == null) {
				return 1;
			}
			if (o2 == null) {
				return -1;
			}
			for (int i = 0; i < o1.length && i < o2.length; i++) {
				final int cmp = Byte.compare(o1[i], o2[i]);
				if (cmp != 0) {
					return cmp;
				}
			}
			return Integer.compare(o1.length, o2.length);
		}
	}

	@Value
	private static class InsertedObject {
		private FileMode fileMode;
		private AnyObjectId id;
	}

	private static interface InsertHandler {
		ObjectId insert(ObjectInserter inserter) throws IOException;
	}

	@Value
	public static class LogAnalyzeResult {
		private int commitCount;
		private Optional<Instant> lastCommitTime;
	}

	private static final class RepositoryObjectLoader implements ObjectLoaderLookup {
		private final Repository repository;

		private RepositoryObjectLoader(final Repository repository) {
			this.repository = repository;
		}

		@Override
		public ObjectLoader createLoader(final ObjectId object) throws IOException {
			return repository.open(object);
		}
	}

	private static final ObjectMapper _OM = new ObjectMapper();

	private static final String _REFS_HEADS = "refs/heads/";
	private static final ObjectReader ALBUM_METADATA_READER = _OM.readerFor(AlbumMetadata.class);

	private static final ObjectWriter ALBUM_METADATA_WRITER = _OM.writerFor(AlbumMetadata.class);

	private static final String AUTOADD_FILE = ".autoadd";

	private static final String COMMIT_ID_FILE = "commit-id";

	private static final String CONFIG_JSON = "config.json";
	private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[] {	DateTimeFormatter.ISO_INSTANT,
																																														DateTimeFormatter.ISO_ZONED_DATE_TIME,
																																														DateTimeFormatter.ISO_LOCAL_DATE_TIME,
																																														DateTimeFormatter.ISO_LOCAL_DATE };
	private static final String MASTER_REF = _REFS_HEADS + "master";
	private static final String META_REPOSITORY = ".meta.git";
	private static final String RAOA_JSON = ".raoa.json";

	public static <R> Future<R> completedFuture(final R value) {
		return new CompletedFuture<R>(value);
	}

	private final ConcurrentMap<String, AlbumData> albums = new ConcurrentHashMap<>();
	private final Map<AttachementGenerator, ExecutorService> attachementGeneratorExecutors = new HashMap<AttachementGenerator, ExecutorService>();

	@Autowired
	private List<AttachementGenerator> attachementGenerators;
	@Autowired
	private ServerConfiguration configuration;
	@Autowired
	private HashGenerator hashGenerator;
	private final String instanceId = UUID.randomUUID().toString();

	private final AtomicReference<ArchiveMetadataHolder> lastMetadataState = new AtomicReference<BareGitAlbumAccess.ArchiveMetadataHolder>(null);

	@Override
	public boolean addAutoaddBeginDate(final String album, final Instant autoAddDate) {
		try {
			final AlbumData albumData = albums.get(album);
			final AlbumCache cache = getAlbumCache(albumData);
			if (cache == null) {
				return false;
			}
			if (cache.getAutoAddBeginDates().contains(autoAddDate)) {
				return true;
			}
			final Set<Instant> newEntries = new TreeSet<>(cache.getAutoAddBeginDates());
			newEntries.add(autoAddDate);
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			{
				@Cleanup
				final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
				for (final Instant instant : newEntries) {
					printWriter.println(DateTimeFormatter.ISO_INSTANT.format(instant));
				}
			}
			final byte[] newData = baos.toByteArray();
			return updateAlbum(albumData, "new autoadd-date: " + autoAddDate, Collections.singletonMap(AUTOADD_FILE, inserter -> inserter.insert(Constants.OBJ_BLOB, newData)));
		} catch (final IOException ex) {
			throw new RuntimeException("Cannot edit album " + album, ex);
		}
	}

	@Override
	public Mono<String> createAlbum(final String[] pathComps) {
		return Mono.defer(() -> {
			final Path basePath = FileSystems.getDefault().getPath(configuration.getAlbumBaseDir()).toAbsolutePath();
			final Path albumPath = FileSystems.getDefault().getPath(configuration.getAlbumBaseDir(), pathComps).normalize().toAbsolutePath();
			if (!albumPath.startsWith(basePath)) {
				return Mono.error(new IllegalArgumentException("Invalid path: " + albumPath));
			}
			final Path parentPath = albumPath.getParent();
			final String lastComp = albumPath.toFile().getName();
			if (lastComp.endsWith(".git")) {
				return Mono.error(new IllegalArgumentException("Illegal album name: " + Arrays.toString(pathComps)));
			}
			final File newAlbumDir = new File(parentPath.toFile(), lastComp + ".git");
			final String pathName = relative(basePath, albumPath);
			final String hash = hashGenerator.generateHash(pathName);
			if (newAlbumDir.exists()) {
				for (final Entry<String, AlbumData> albumEntry : albums.entrySet()) {
					if (newAlbumDir.equals(albumEntry.getValue().getAlbumRepository().getDirectory())) {
						return Mono.just(albumEntry.getKey());
					}
				}
				return Mono.error(new IllegalArgumentException("Directory " + pathName + " already exists"));
			}
			try {
				final Git git = Git.init().setBare(true).setDirectory(newAlbumDir).call();
				final Repository repository = git.getRepository();
				final AlbumData albumData = AlbumData.builder().fullAlbumName(pathName).albumRepository(repository).build();
				final AlbumMetadata metadata = new AlbumMetadata(hash, lastComp, null);
				updateAlbumMeta(albumData, null, metadata);
				albums.put(hash, albumData);
				return Mono.just(hash);
			} catch (final IllegalStateException | GitAPIException | IOException e) {
				return Mono.error(new RuntimeException("Cannot create new album " + pathName, e));
			}
		});
	}

	protected CommitBuilder createCommit(final Repository repository, final ObjectId parentCommit, final ObjectId treeObjectId)	throws MissingObjectException,
																																																															IncorrectObjectTypeException,
																																																															IOException {
		final CommitBuilder commitBuilder = new CommitBuilder();
		if (parentCommit != null) {
			try (RevWalk walk = new RevWalk(repository)) {
				final RevCommit commit = walk.parseCommit(parentCommit);
				final RevTree tree = commit.getTree();
				if (tree.getId().equals(treeObjectId)) {
					log.warn("No change -> Skip commit");
					return null;
				}
			}
			commitBuilder.setParentId(parentCommit);
		}
		commitBuilder.setTreeId(treeObjectId);
		final PersonIdent author = new PersonIdent("Server ", "dummy@none");
		commitBuilder.setAuthor(author);
		commitBuilder.setCommitter(author);
		return commitBuilder;
	}

	private ImageDataHandler createImageDataHandler(final Supplier<Optional<String>> filename,
																									final Supplier<Optional<AlbumEntryData>> albumEntrySupplier,
																									final Optional<AnyObjectId> objectId,
																									final Repository albumRepository) {
		final Supplier<Optional<AlbumEntryMetadata>> entryMetadata = new CachingSupplier<>(() -> {
			final Optional<AlbumEntryData> albumEntryOptional = albumEntrySupplier.get();
			if (!albumEntryOptional.isPresent()) {
				return Optional.empty();
			}
			final ObjectId file = albumEntryOptional.get().getGeneratedAttachements().get(MetadataAttachementGenerator.ATTACHEMENT_NAME);
			if (file == null) {
				return Optional.empty();
			}
			try {
				final ObjectLoader objectLoader = albumRepository.open(file, Constants.OBJ_BLOB);
				@Cleanup
				final InputStream stream = objectLoader.openStream();
				return Optional.of(_OM.readerFor(AlbumEntryMetadata.class).readValue(stream));
			} catch (final IOException e) {
				log.error("Cannot open metadata of " + filename, e);
				return Optional.empty();
			}
		});
		final Supplier<AnyObjectId> objectIdSupplier;
		if (objectId.isPresent()) {
			final AnyObjectId anyObjectId = objectId.get();
			objectIdSupplier = () -> anyObjectId;
		} else {
			objectIdSupplier = () -> albumEntrySupplier.get().get().getOriginalFileId();
		}
		return new ImageDataHandler() {

			@Override
			public Optional<String> getCameraMake() {
				return entryMetadata.get().map(m -> m.getCameraMake());
			}

			@Override
			public Optional<String> getCameraModel() {
				return entryMetadata.get().map(m -> m.getCameraModel());
			}

			@Override
			public Optional<String> getCaption() {
				return entryMetadata.get().map(m -> m.getCaption());
			}

			@Override
			public Optional<Instant> getCaptureDate() {
				return entryMetadata.get().map(m -> m.getCaptureDate().toInstant());
			}

			@Override
			public Optional<Double> getExposureTime() {
				return entryMetadata.get().map(m -> m.getExposureTime());
			}

			@Override
			public Optional<Double> getFNumber() {
				return entryMetadata.get().map(m -> m.getFNumber());
			}

			@Override
			public Optional<Double> getFocalLength() {
				return entryMetadata.get().map(m -> m.getFocalLength());
			}

			@Override
			public String getId() {
				return objectIdSupplier.get().name();
			}

			@Override
			public Optional<Integer> getIso() {
				return entryMetadata.get().map(m -> m.getIso());
			}

			@Override
			public Collection<String> getKeywords() {
				return entryMetadata.get().map(m -> m.getKeywords()).orElse(Collections.emptyList());
			}

			@Override
			public Optional<Instant> getLastModified() {
				return Optional.empty();
			}

			@Override
			public Optional<String> getName() {
				return filename.get();
			}

			@Override
			public Optional<Long> getOriginalFileSize() {
				try {
					return Optional.of(albumRepository.open(objectIdSupplier.get()).getSize());
				} catch (final IOException e) {
					log.error("Cannot access file " + filename, e);
					return Optional.empty();
				}
			}

			@Override
			public Optional<Boolean> isVideo() {
				return entryMetadata.get().map(m -> m.isVideo());
			}

			@Override
			public Optional<FileContent> mobileData() throws IOException {
				final String fileId = getId();
				final Optional<ObjectId> foundAttachement = findSingleFile(albumRepository, _REFS_HEADS + MobileAttachementGenerator.ATTACHEMENT_TYPE, fileId);
				return foundAttachement.map(o -> () -> albumRepository.open(o, Constants.OBJ_BLOB).openStream());
			}

			@Override
			public Optional<FileContent> thumnbailData() throws IOException {
				final String fileId = getId();
				final Optional<ObjectId> foundAttachement = findSingleFile(albumRepository, _REFS_HEADS + ThumbnailAttachementGenerator.ATTACHEMENT_TYPE, fileId);
				return foundAttachement.map(o -> () -> albumRepository.open(o, Constants.OBJ_BLOB).openStream());
			}
		};
	}

	private void enqueueThumbnailUpdate(final String repoName, final AlbumData albumData, final AttachementGenerator attachementGenerator) {

		final String attachementRef = attachementGenerator.attachementType();
		try {
			attachementGeneratorExecutors.get(attachementGenerator).submit(new Runnable() {

				private boolean flush(final SortedMap<String, ObjectId> pendingObjects, final AlbumData albumData, final ObjectId lastThumbnailCommit) throws IOException {
					final Repository repository = albumData.getAlbumRepository();
					final ObjectId thumbnailRefBefore = repository.resolve(_REFS_HEADS + attachementRef);
					final TreeFormatter thumbnailTreeFormatter = new TreeFormatter();
					for (final Entry<String, ObjectId> fileEntry : pendingObjects.entrySet()) {
						thumbnailTreeFormatter.append(fileEntry.getKey(), FileMode.REGULAR_FILE, fileEntry.getValue());
					}
					final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
					final ObjectId treeObjectId = inserter.insert(thumbnailTreeFormatter);
					final CommitBuilder commitBuilder = createCommit(repository, thumbnailRefBefore, treeObjectId);
					if (commitBuilder == null) {
						return true;
					}
					final ObjectId commitId = inserter.insert(commitBuilder);
					inserter.flush();
					final RefUpdate ru = repository.updateRef(_REFS_HEADS + attachementRef);
					ru.setNewObjectId(commitId);
					ru.setRefLogMessage("thumbnails added", false);
					if (thumbnailRefBefore != null) {
						ru.setExpectedOldObjectId(thumbnailRefBefore);
					} else {
						ru.setExpectedOldObjectId(ObjectId.zeroId());
					}
					final Result result = ru.update();
					log.info("Update result: " + result);
					return Arrays.asList(Result.FAST_FORWARD, Result.NEW).contains(result);
				}

				@Override
				public void run() {
					log.info("Update attachements " + attachementGenerator.attachementType() + " on " + repoName);

					final Repository repository = albumData.getAlbumRepository();
					try {
						final AlbumCache albumEntries = getAlbumCache(albumData);
						if (albumEntries == null) {
							return;
						}
						final AttachementCache existingAttachementState = getAttachementState(albumData, attachementRef);
						if (existingAttachementState != null) {
							if (albumEntries.getLastCommit().getName().equals(existingAttachementState.getCommitId())) {
								// all thumbnails are up to date
								return;
							}
						}
						final ObjectId lastThumbnailCommit = existingAttachementState.getLastAttachementCommit();
						final Map<String, Future<ObjectId>> pendingObjects = new HashMap<>();
						for (final Entry<String, AlbumEntryData> entry : albumEntries.getEntries().entrySet()) {
							final AlbumEntryData albumDataEntry = entry.getValue();
							final String attachementFilename = attachementGenerator.createAttachementFilename(albumDataEntry);
							if (attachementFilename == null) {
								continue;
							}
							final ObjectId existingThumbnail = getAttachementState(albumData, attachementRef).getExistingAttachements().get(attachementFilename);
							if (existingThumbnail != null) {
								pendingObjects.put(attachementFilename, completedFuture(existingThumbnail));
								continue;
							}
							pendingObjects.put(	attachementFilename,
																	attachementGenerator.generateAttachement(	entry.getValue(),
																																						new RepositoryObjectLoader(repository),
																																						repository.getObjectDatabase().newInserter()));
						}
						final SortedMap<String, ObjectId> entriesOfCommit = new TreeMap<String, ObjectId>();
						int lastFlushSize = 0;
						do {
							final long waitUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20);
							for (final Iterator<java.util.Map.Entry<String, Future<ObjectId>>> pendingObjectsIterator = pendingObjects.entrySet()
																																																												.iterator(); pendingObjectsIterator.hasNext();) {
								final Entry<String, Future<ObjectId>> futureEntry = pendingObjectsIterator.next();
								try {
									final Future<ObjectId> future = futureEntry.getValue();
									if (future != null) {
										final ObjectId attachementId = future.get(waitUntil - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
										if (attachementId != null) {
											entriesOfCommit.put(futureEntry.getKey(), attachementId);
										}
									}
									pendingObjectsIterator.remove();
								} catch (final TimeoutException ex) {
									// not finished yet -> try next time
								}
							}
							if (pendingObjects.isEmpty()) {
								final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
								final ObjectId commitIdFile = inserter.insert(Constants.OBJ_BLOB, albumEntries.getLastCommit().getName().getBytes());
								entriesOfCommit.put(COMMIT_ID_FILE, commitIdFile);
								inserter.flush();
							}
							if (lastFlushSize != entriesOfCommit.size()) {
								flush(entriesOfCommit, albumData, lastThumbnailCommit);
								lastFlushSize = entriesOfCommit.size();
							}
						} while (!pendingObjects.isEmpty());

					} catch (final IOException | InterruptedException | ExecutionException e) {
						log.error("cannot generate thumbnails of " + repository, e);
					}
				}
			});
		} catch (final RejectedExecutionException e) {
			// log.info("queue full -> skip insertion");
		}
	}

	private Optional<ObjectId> findSingleFile(final Repository repository, final String refName, final String filename) throws IOException {
		final ObjectId ref = repository.resolve(refName);
		if (ref == null) {
			return Optional.empty();
		}
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);

		final RevCommit commit = revWalk.parseCommit(ref);
		final RevTree tree = commit.getTree();
		treeWalk.addTree(tree);
		final TreeFilter treeFilter = PathFilterGroup.createFromStrings(filename);
		treeWalk.setRecursive(treeFilter.shouldBeRecursive());
		treeWalk.setFilter(treeFilter);
		while (treeWalk.next()) {
			if (treeWalk.isSubtree()) {
				continue;
			}
			final String pathString = treeWalk.getPathString();
			if (pathString.equals(filename)) {
				final ObjectId objectId = treeWalk.getObjectId(0);
				return Optional.of(objectId);
			}
		}
		return Optional.empty();
	}

	private AlbumCache getAlbumCache(final AlbumData albumData) throws IOException {
		if (albumData == null) {
			return null;
		}
		while (true) {
			synchronized (albumData) {
				final WeakReference<AlbumCache> albumStateWeakReference = albumData.getAlbumCacheReference().get();
				final BranchState currentState = albumData.getCurrentState().get();
				if (albumStateWeakReference != null && currentState != null) {
					final AlbumCache albumCache = albumStateWeakReference.get();
					if (albumCache != null) {
						if (albumCache.getLastCommit().equals(albumCache.getLastCommit()) && (System.currentTimeMillis() - currentState.getLastRefreshTime()) < 10 * 1000) {
							return albumCache;
						}
					}
				}
				if (!updateAlbumState(albumData)) {
					return null;
				}
			}
		}
	}

	@Override
	public Optional<AlbumDataHandler> getAlbumData(final String albumid) {
		final AlbumData albumData = albums.get(albumid);

		final Supplier<ArchiveData> archive = new CachingSupplier<>(() -> getArchiveData());

		return Optional.ofNullable(albumData).map((Function<AlbumData, AlbumDataHandler>) data -> new AlbumDataHandler() {

			final Supplier<Optional<AlbumCache>> albumCache = new CachingSupplier<>(() -> {
				try {
					return Optional.of(getAlbumCache(data));
				} catch (final IOException e) {
					log.error("Cannot take album cache", e);
					return Optional.empty();
				}
			});

			final Supplier<Optional<LogAnalyzeResult>> logAnalyzeResult = new CachingSupplier<>(() -> {
				try {
					final Repository repository = data.getAlbumRepository();
					final Iterable<RevCommit> log = new Git(repository).log().add(repository.resolve(MASTER_REF)).call();
					int count = 0;
					int lastCommitTime = 0;
					for (final RevCommit revCommit : log) {
						lastCommitTime = revCommit.getCommitTime();
						count += 1;
					}
					return Optional.of(new LogAnalyzeResult(count, count > 0 ? Optional.of(Instant.ofEpochMilli(TimeUnit.SECONDS.toMillis(lastCommitTime))) : Optional.empty()));
				} catch (final IOException | RevisionSyntaxException | GitAPIException e) {
					log.error("Cannot take album cache", e);
					return Optional.empty();
				}
			});

			@Override
			public String getAlbumName() {
				final String name = data.getFullAlbumName();
				return name.substring(0, name.length() - 4);
			}

			@Override
			public Optional<String> getAlbumTitle() {
				return albumCache.get().map(cache -> cache.getAlbumMetadata().getAlbumTitle());
			}

			@Override
			public Optional<String> getAlbumTitleEntry() {
				return albumCache.get().map(cache -> cache.getAlbumMetadata().getTitleEntry());
			}

			@Override
			public Collection<Instant> getAutoAddDates() {
				return getAutoaddBeginDates(albumid);
			}

			@Override
			public Optional<Instant> getCaptureTime() {
				final ch.bergturbenthal.raoa.json.AlbumMetadata albumMetadata = getAlbumMetadata(albumid);
				if (albumMetadata == null) {
					return Optional.empty();
				}
				final Date date = albumMetadata.getTimestamp();
				if (date == null) {
					return Optional.empty();
				}
				return Optional.of(date.toInstant());
			}

			@Override
			public Collection<String> getClients() {
				final ArchiveData archiveData = archive.get();
				final String albumName = getAlbumName();
				final Collection<String> clientList = new ArrayList<>();
				for (final Entry<String, StorageData> storageEntry : archiveData.getStorages().entrySet()) {
					if (storageEntry.getValue().getAlbumList().contains(albumName)) {
						clientList.add(storageEntry.getKey());
					}
				}
				return clientList;
			}

			@Override
			public int getCommitCount() {
				return logAnalyzeResult.get().map(r -> r.getCommitCount()).orElse(0);
			}

			@Override
			public Optional<Instant> getLastModified() {
				return logAnalyzeResult.get().map(r -> r.getLastCommitTime().orElse(null));
			}

			@Override
			public Collection<ImageDataHandler> listImages() {
				return albumCache.get().map(cache -> {
					final Repository albumRepository = albumData.getAlbumRepository();
					final Collection<ImageDataHandler> ret = new ArrayList<>();
					final Map<String, AlbumEntryData> entries = cache.getEntries();
					for (final Entry<String, AlbumEntryData> fileEntry : entries.entrySet()) {
						final String filename = fileEntry.getKey();
						final AlbumEntryData albumEntryData = fileEntry.getValue();
						ret.add(createImageDataHandler(() -> Optional.of(filename), () -> Optional.of(albumEntryData), Optional.empty(), albumRepository));
					}
					return ret;
				}).orElse(Collections.emptyList());
			}

		});

	}

	@Override
	public ch.bergturbenthal.raoa.json.AlbumMetadata getAlbumMetadata(final String albumId) {
		try {
			final AlbumData albumData = albums.get(albumId);
			if (albumData == null) {
				return null;
			}
			final AlbumCache albumCache = getAlbumCache(albumData);
			final AlbumMetadata albumMetadata = albumCache.getAlbumMetadata();
			final AlbumMetadataBuilder builder = ch.bergturbenthal.raoa.json.AlbumMetadata.builder();
			builder.name(albumMetadata.getAlbumTitle());
			builder.id(albumMetadata.getAlbumId());
			final Map<String, AlbumEntryData> entries = albumCache.getEntries();
			final Repository repository = albumData.getAlbumRepository();
			if (entries != null) {
				long dateSum = 0;
				int dateCount = 0;
				for (final AlbumEntryData entryData : entries.values()) {
					final ObjectId objectId = entryData.getGeneratedAttachements().get(MetadataAttachementGenerator.ATTACHEMENT_NAME);
					if (objectId == null) {
						continue;
					}
					final AlbumEntryMetadata rawMetadata = _OM.readerFor(AlbumEntryMetadata.class).readValue(repository.open(objectId).getBytes());
					final Date gpsDate = rawMetadata.getGpsDate();
					if (gpsDate != null) {
						dateSum += gpsDate.getTime();
						dateCount += 1;
					} else {
						final Date captureDate = rawMetadata.getCaptureDate();
						if (captureDate != null) {
							dateSum += captureDate.getTime();
							dateCount += 1;
						}
					}
				}
				if (dateCount > 0) {
					builder.timestamp(new Date(dateSum / dateCount));
				}
			}
			return builder.build();
		} catch (final IOException e) {
			log.error("Canot load Album data" + albumId);
		}
		return null;
	}

	private ArchiveData getArchiveData() {
		final ArchiveMetadataHolder archiveMetadataHolder = lastMetadataState.get();
		if (archiveMetadataHolder != null) {
			final Instant lastLoadTime = archiveMetadataHolder.getLastLoadTime();
			if (Duration.between(lastLoadTime, Instant.now()).getSeconds() < 10) {
				return archiveMetadataHolder.getArchiveData();
			}
		}
		try {
			final Repository metadataRepository = getMetadataRespository();
			final Optional<ObjectId> objectId = findSingleFile(metadataRepository, MASTER_REF, CONFIG_JSON);
			return objectId.map(o -> {
				try {
					final ObjectLoader objectLoader = metadataRepository.getObjectDatabase().open(o, Constants.OBJ_BLOB);
					@Cleanup
					final ObjectStream stream = objectLoader.openStream();
					return new ObjectMapper().readValue(stream, ArchiveData.class);

				} catch (final IOException e) {
					log.error("Cannot load metadata", e);
					return null;
				}
			}).orElse(null);
		} catch (final Exception ex) {
			throw new RuntimeException("Error accessing archive metadata", ex);
		}
	}

	private AttachementCache getAttachementState(final AlbumData data, final String attachementType) throws IOException {
		if (data == null) {
			return null;
		}
		final String attachementRef = _REFS_HEADS + attachementType;
		final WeakReference<AttachementCache> thumbnailStateReference = data.getAttachementCaches().get(attachementRef);
		if (thumbnailStateReference != null) {
			final AttachementCache thumbnailState = thumbnailStateReference.get();
			if (thumbnailState != null) {
				if ((System.currentTimeMillis() - thumbnailState.getCreateTime()) < 10 * 1000) {
					return thumbnailState;
				}
			}
		}
		synchronized (data) {
			if (thumbnailStateReference != null) {
				final AttachementCache thumbnailState = thumbnailStateReference.get();
				if (thumbnailState != null) {
					if ((System.currentTimeMillis() - thumbnailState.getCreateTime()) < 10 * 1000) {
						return thumbnailState;
					}
				}
			}
			final AttachementCache createdResult = walkAttachement(data.getAlbumRepository(), attachementRef);
			if (createdResult != null) {
				data.getAttachementCaches().put(attachementRef, new WeakReference<AttachementCache>(createdResult));
			}
			return createdResult;
		}
	}

	@Override
	public Collection<Instant> getAutoaddBeginDates(final String album) {
		final AlbumData albumData = albums.get(album);
		if (albumData == null) {
			return Collections.emptyList();
		}
		try {
			final AlbumCache albumCache = getAlbumCache(albumData);
			return albumCache.getAutoAddBeginDates();
		} catch (final IOException e) {
			throw new RuntimeException("Cannot load album state", e);
		}
	}

	@Override
	public InstanceData getInstanceData() {
		final String configuredServerName = configuration.getServerName();
		if (configuredServerName != null) {
			return InstanceData.builder().instanceId(instanceId).instanceName(configuredServerName).build();
		}
		final String albumBaseDir = configuration.getAlbumBaseDir();
		if (albumBaseDir == null) {
			return null;
		}
		final File albumBaseFile = new File(albumBaseDir, ".bareid");
		if (!albumBaseFile.exists()) {
			return null;
		}
		try {
			final InstanceDataBuilder instanceDataBuilder = InstanceData.builder().instanceId(instanceId);
			final List<String> lines = IOUtils.readLines(new FileInputStream(albumBaseFile), "utf-8");
			for (final String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				instanceDataBuilder.instanceName(line.trim());
				break;
			}
			return instanceDataBuilder.build();
		} catch (final IOException e) {
			log.error("Cannot read " + albumBaseFile, e);
		}
		return null;
	}

	private Repository getMetadataRespository() throws IOException, IllegalStateException, GitAPIException {
		final String albumBaseDir = configuration.getAlbumBaseDir();
		final File albumDir = new File(new File(albumBaseDir), META_REPOSITORY);
		if (albumDir.exists()) {
			return new RepositoryBuilder().setGitDir(albumDir).setBare().setMustExist(true).build();
		}
		final Git createdGit = Git.init().setDirectory(albumDir).setBare(true).call();
		final Repository repository = createdGit.getRepository();
		final boolean createSuccessful = updateFilesOfBranch(repository, MASTER_REF, Collections.singletonMap(CONFIG_JSON, inserter -> {
			final ArchiveData emptyArchive = new ArchiveData();
			emptyArchive.setArchiveName("Unnamed Archive");
			return inserter.insert(Constants.OBJ_BLOB, new ObjectMapper().writeValueAsBytes(emptyArchive));
		}), "Initial Commit");
		if (!createSuccessful) {
			throw new RuntimeException("Cannot create initial commit on " + albumDir);
		}
		return repository;
	}

	@PostConstruct
	public void initExecutors() {
		for (final AttachementGenerator attachementGenerator : attachementGenerators) {
			attachementGeneratorExecutors.put(attachementGenerator, new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
		}
	}

	private SortedMap<String, ObjectId> insertObjects(final ObjectInserter inserter, final Map<String, InsertHandler> updateHandlers) throws IOException {
		final SortedMap<String, ObjectId> insertedObjects = new TreeMap<>();
		// store all objects into db
		for (final Entry<String, InsertHandler> updateEntry : updateHandlers.entrySet()) {
			final String filename = updateEntry.getKey();
			final InsertHandler insertHandler = updateEntry.getValue();
			if (insertHandler == null) {
				insertedObjects.put(filename, null);
			} else {
				final ObjectId newObject = insertHandler.insert(inserter);
				insertedObjects.put(filename, newObject);
			}
		}
		return insertedObjects;
	}

	@Override
	public Flux<String> listAlbums() {
		// final List<String> ret = new ArrayList<String>();
		// for (final Entry<String, AlbumData> albumEntry : albums.entrySet()) {
		// try {
		// final AlbumCache entries = getAlbumEntries(albumEntry.getValue());
		// final AlbumMetadata metadata = entries.getAlbumMetadata();
		// log.info("Metadata of " + albumEntry.getKey() + " loaded");
		// ret.add(albumEntry.getKey());
		// } catch (final IOException e) {
		// log.error("Cannot load album data of " + albumEntry.getKey(), e);
		// }
		// }
		return Flux.fromIterable(albums.keySet());
	}

	private void notifyFoundAlbum(final AlbumData albumData) {
		// TODO Auto-generated method stub

	}

	private void notifyModifiedAlbum(final BranchState branchStateBefore, final AlbumData albumData) throws IOException {
		final AlbumCache newAlbumEntries = getAlbumCache(albumData);
		// TODO Auto-generated method stub

	}

	private void notifyRemovedAlbum(final AlbumData removedAlbum) {
		// TODO Auto-generated method stub

	}

	private Instant parseInstant(final String line) {
		final List<DateTimeException> exceptions = new ArrayList<>();
		for (final DateTimeFormatter format : DATE_TIME_FORMATTERS) {
			try {
				final TemporalAccessor temporal = format.parse(line);
				if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
					return (Instant.from(temporal));
				}
				if (temporal.isSupported(ChronoField.EPOCH_DAY)) {
					return LocalDate.from(temporal).atStartOfDay(ZoneOffset.UTC).toInstant();
				}
			} catch (final DateTimeException ex) {
				exceptions.add(ex);
			}
		}
		log.error("Cannot parse date " + line);
		for (final DateTimeException dateTimeException : exceptions) {
			log.error("Error: ", dateTimeException);
		}
		return null;
	}

	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000 * 10)
	public void refreshAlbums() {

		final String albumBaseDir = configuration.getAlbumBaseDir();
		updateAlbumList(albumBaseDir);
		for (final Entry<String, AlbumData> albumEntry : albums.entrySet()) {
			final AlbumData albumData = albumEntry.getValue();
			try {
				// final long startAlbumLoad = System.currentTimeMillis();
				final AlbumCache albumState = getAlbumCache(albumData);
				final ObjectId masterRef = albumState.getLastCommit();
				// log.info("Loaded directory of " + albumEntries.size() + " entries in " + (System.currentTimeMillis() - startAlbumLoad) + " ms");
				for (final AttachementGenerator attachementGenerator : attachementGenerators) {
					final AttachementCache attachementState = getAttachementState(albumData, attachementGenerator.attachementType());
					final String foundCommitId = attachementState.getCommitId();
					if (foundCommitId == null || !masterRef.getName().equals(foundCommitId)) {
						enqueueThumbnailUpdate(albumData.getFullAlbumName(), albumData, attachementGenerator);
					}
				}
			} catch (final IOException e) {
				log.error("Cannot load data from " + albumData.getAlbumRepository(), e);
			}
		}
	}

	protected String relative(final Path basePath, final Path completePath) {
		final Path relativePath = basePath.relativize(completePath);
		final StringBuilder pathBuilder = new StringBuilder();
		for (int i = 0; i < relativePath.getNameCount(); i++) {
			if (pathBuilder.length() > 0) {
				pathBuilder.append('/');
			}
			pathBuilder.append(relativePath.getName(i).toString());
		}
		final String pathName = pathBuilder.toString();
		return pathName;
	}

	@Override
	public Optional<ImageDataHandler> takeImageById(final String albumId, final String imageId) {
		try {
			final AlbumData albumData = albums.get(albumId);
			if (albumData == null) {
				return Optional.empty();
			}
			final AlbumCache albumCache = getAlbumCache(albumData);
			if (albumCache == null) {
				return Optional.empty();
			}

			final Repository albumRepository = albumData.getAlbumRepository();

			final ObjectId objectId = ObjectId.fromString(imageId);
			final Supplier<Optional<String>> filename = () -> {
				for (final Entry<String, ObjectId> fileEntry : albumCache.getFiles().entrySet()) {
					if (fileEntry.getValue().equals(objectId)) {
						return Optional.of(fileEntry.getKey());
					}
				}
				return Optional.empty();
			};
			final Supplier<Optional<AlbumEntryData>> albumEntrySupplier = () -> filename.get().map(f -> albumCache.getEntries().get(f));
			return Optional.of(createImageDataHandler(filename, albumEntrySupplier, Optional.of(objectId), albumRepository));
		} catch (final IOException e) {
			log.error("Cannot access to image " + albumId + "/" + imageId, e);
			return Optional.empty();
		}
	}

	private boolean updateAlbum(final AlbumData albumData, final String message, final Map<String, InsertHandler> updateHandlers)	throws IOException,
																																																																AmbiguousObjectException,
																																																																IncorrectObjectTypeException,
																																																																MissingObjectException,
																																																																CorruptObjectException {
		final Repository repository = albumData.getAlbumRepository();
		return updateFilesOfBranch(repository, MASTER_REF, updateHandlers, message);
	}

	private void updateAlbumList(final String albumBaseDir) {
		if (albumBaseDir != null) {
			final File albumBaseFile = new File(albumBaseDir);
			final FileSystem fs = FileSystems.getDefault();
			final Path albumBasePath = fs.getPath(albumBaseFile.getPath());
			final FindGitDirWalker gitDirWalker = new FindGitDirWalker();
			final HashSet<String> remainingAlbums = new HashSet<>(albums.keySet());
			for (final File foundDir : gitDirWalker.findGitDirs(albumBaseFile).toIterable()) {
				log.info("Found Repository: " + foundDir);
				final Instant repositoryStartTime = Instant.now();
				final Path albumPath = fs.getPath(foundDir.getPath());
				final String pathName = relative(albumBasePath, albumPath);
				try {
					final Repository repository = new FileRepositoryBuilder().readEnvironment().setGitDir(foundDir).setMustExist(true).setBare().build();
					final AlbumData albumData = AlbumData.builder().fullAlbumName(pathName).albumRepository(repository).build();
					final AlbumCache albumAlbumCache = getAlbumCache(albumData);
					if (albumAlbumCache == null) {
						continue;
					}
					final AlbumMetadata albumMetadata = albumAlbumCache.getAlbumMetadata();
					final String albumKey;
					if (albumMetadata == null) {
						final AlbumMetadata newMetadata = new AlbumMetadata();
						newMetadata.setAlbumId(hashGenerator.generateHash(pathName));
						newMetadata.setAlbumTitle(foundDir.getName());
						final Map<String, AlbumEntryData> entries = albumAlbumCache.getEntries();
						if (entries != null && !entries.isEmpty()) {
							newMetadata.setTitleEntry(entries.keySet().iterator().next());
						}
						final boolean updateOk = updateAlbumMeta(albumData, null, newMetadata);
						if (!updateOk) {
							log.warn("Cannot update write metadata to " + pathName);
							continue;
						}
						albumKey = newMetadata.getAlbumId();
					} else if (albumMetadata.getAlbumId() == null || albumMetadata.getAlbumTitle() == null || albumMetadata.getTitleEntry() == null) {
						final AlbumMetadata newAlbumMeta = new AlbumMetadata();
						if (albumMetadata.getAlbumId() == null) {
							newAlbumMeta.setAlbumId(hashGenerator.generateHash(pathName));
						} else {
							newAlbumMeta.setAlbumId(albumMetadata.getAlbumId());
						}
						if (albumMetadata.getAlbumTitle() == null) {
							newAlbumMeta.setAlbumTitle(foundDir.getName());
						} else {
							newAlbumMeta.setAlbumTitle(albumMetadata.getAlbumTitle());
						}
						if (albumMetadata.getTitleEntry() == null) {
							final Map<String, AlbumEntryData> entries = albumAlbumCache.getEntries();
							if (entries != null && !entries.isEmpty()) {
								newAlbumMeta.setTitleEntry(entries.keySet().iterator().next());
							}
						} else {
							newAlbumMeta.setTitleEntry(albumMetadata.getTitleEntry());
						}
						final boolean updateOk = updateAlbumMeta(albumData, albumMetadata, newAlbumMeta);
						if (!updateOk) {
							log.warn("Cannot update write metadata to " + pathName);
							continue;
						}
						albumKey = newAlbumMeta.getAlbumId();
					} else {
						albumKey = albumMetadata.getAlbumId();
					}
					albums.put(albumKey, albumData);
					remainingAlbums.remove(albumKey);
				} catch (final IOException e) {
					log.error("Cannot load repository " + pathName, e);
				} finally {
					log.info("Ended repository " + foundDir + ", Duration: " + Duration.between(repositoryStartTime, Instant.now()));
				}
			}
			for (final String remainingAlbum : remainingAlbums) {
				final AlbumData removedAlbum = albums.remove(remainingAlbum);
				if (removedAlbum != null) {
					notifyRemovedAlbum(removedAlbum);
				}
			}
		} else {
			log.error("no base dir configured");
		}
	}

	protected boolean updateAlbumMeta(final AlbumData albumData, final AlbumMetadata metadataBefore, final AlbumMetadata metadataAfter)	throws IOException,
																																																																			MissingObjectException,
																																																																			JsonProcessingException,
																																																																			IncorrectObjectTypeException,
																																																																			CorruptObjectException {
		final AlbumCache albumState = getAlbumCache(albumData);
		if (albumState == null && metadataBefore != null) {
			return false;
		}
		final Repository repository = albumData.getAlbumRepository();
		final ObjectId objectIdBefore;
		if (albumState != null) {
			final Map<String, ObjectId> files = albumState.getFiles();
			objectIdBefore = files.get(RAOA_JSON);
			if (objectIdBefore != null) {
				@Cleanup
				final ObjectStream beforeStream = repository.open(objectIdBefore).openStream();
				final AlbumMetadata storedMetadataBefore = ALBUM_METADATA_READER.readValue(beforeStream);
				if (!Objects.equals(metadataBefore, storedMetadataBefore)) {
					return false;
				}
			} else {
				if (metadataBefore != null) {
					return false;
				}
			}
		} else {
			objectIdBefore = null;
		}
		final InsertHandler value = new InsertHandler() {

			@Override
			public ObjectId insert(final ObjectInserter inserter) throws IOException {
				final byte[] newValue = ALBUM_METADATA_WRITER.writeValueAsBytes(metadataAfter);
				return inserter.insert(Constants.OBJ_BLOB, newValue);
			}
		};
		return updateFilesOfBranch(repository, MASTER_REF, Collections.singletonMap(RAOA_JSON, value), "metadata updated");
	}

	protected boolean updateAlbumState(final AlbumData albumData) throws IOException {

		final Map<String, AlbumEntryData> albumEntries = new HashMap<>();
		final Repository repository = albumData.getAlbumRepository();
		final ObjectId masterRef = repository.resolve(MASTER_REF);
		if (masterRef == null) {
			return false;
		}
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);

		final RevCommit commit = revWalk.parseCommit(masterRef);
		final RevTree tree = commit.getTree();
		treeWalk.addTree(tree);

		final Map<String, ObjectId> fileEntries = new LinkedHashMap<>();
		while (treeWalk.next()) {
			if (treeWalk.isSubtree()) {
				continue;
			}
			final ObjectId objectId = treeWalk.getObjectId(0);
			final String pathString = treeWalk.getPathString();
			fileEntries.put(pathString, objectId);
			albumEntries.put(pathString, AlbumEntryData.builder().filename(pathString).originalFileId(objectId).build());
		}
		for (final Iterator<java.util.Map.Entry<String, AlbumEntryData>> entryIterator = albumEntries.entrySet().iterator(); entryIterator.hasNext();) {
			final Entry<String, AlbumEntryData> fileEntry = entryIterator.next();
			final AlbumEntryData entryData = fileEntry.getValue();
			final Map<Class<? extends Object>, Set<ObjectId>> attachedFiles = entryData.getAttachedFiles();
			for (final AttachementGenerator attachementGenerator : attachementGenerators) {
				final Map<Class<? extends Object>, Set<ObjectId>> files = attachementGenerator.findAdditionalFiles(	entryData,
																																																						fileEntries,
																																																						new RepositoryObjectLoader(repository));
				if (files != null) {
					attachedFiles.putAll(files);
				}
			}
			if (attachedFiles.isEmpty()) {
				entryIterator.remove();
			}
		}

		for (final AttachementGenerator attachementGenerator : attachementGenerators) {
			final AttachementCache attachementState = getAttachementState(albumData, attachementGenerator.attachementType());
			if (attachementState != null) {
				for (final Entry<String, AlbumEntryData> albumEntry : albumEntries.entrySet()) {
					final String attachementKey = attachementGenerator.createAttachementFilename(albumEntry.getValue());
					if (attachementKey == null) {
						continue;
					}
					final ObjectId objectId = attachementState.getExistingAttachements().get(attachementKey);
					if (objectId != null) {
						albumEntry.getValue().getGeneratedAttachements().put(attachementGenerator.attachementType(), objectId);
					}
				}
			}
		}
		final ObjectId albumMetaObject = fileEntries.get(RAOA_JSON);
		final AlbumMetadata metadata;
		if (albumMetaObject != null) {
			final ObjectLoader metaObjectloader = repository.open(albumMetaObject);
			@Cleanup
			final ObjectStream metadataStream = metaObjectloader.openStream();
			metadata = ALBUM_METADATA_READER.readValue(metadataStream);
		} else {
			metadata = null;
		}
		final ObjectId autoAddFileObject = fileEntries.get(AUTOADD_FILE);
		final List<Instant> autoAddBeginDates = new ArrayList<>();
		if (autoAddFileObject != null) {
			final ObjectLoader metaObjectloader = repository.open(autoAddFileObject);
			@Cleanup
			final ObjectStream metadataStream = metaObjectloader.openStream();
			final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(metadataStream, StandardCharsets.UTF_8));
			while (true) {
				final String line = bufferedReader.readLine();
				if (line == null) {
					break;
				}
				final Instant parsedInstant = parseInstant(line.trim());
				if (parsedInstant != null) {
					autoAddBeginDates.add(parsedInstant);
				}
			}
		}
		albumData	.getAlbumCacheReference()
							.set(new WeakReference<AlbumCache>(new AlbumCache(metadata, Collections.unmodifiableList(autoAddBeginDates), albumEntries, fileEntries, masterRef)));
		final AtomicReference<BranchState> currentStateRef = albumData.getCurrentState();
		final BranchState branchStateBefore = currentStateRef.get();
		currentStateRef.set(BranchState.builder().lastCommit(masterRef).lastRefreshTime(System.currentTimeMillis()).build());
		if (branchStateBefore == null) {
			notifyFoundAlbum(albumData);
		} else if (!branchStateBefore.getLastCommit().equals(masterRef)) {
			notifyModifiedAlbum(branchStateBefore, albumData);
		}
		return true;
	}

	private Result updateBranch(final String branchRef,
															final String message,
															final Repository repository,

															final SortedMap<String, ObjectId> insertedObjects)	throws AmbiguousObjectException,
																																									IncorrectObjectTypeException,
																																									IOException,
																																									MissingObjectException,
																																									CorruptObjectException {
		final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
		// create new commit
		final SortedMap<byte[], InsertedObject> objectsOfCommit = new TreeMap<>(new ByteArrayComparator());
		for (final Entry<String, ObjectId> entry : insertedObjects.entrySet()) {
			final byte[] rawName = Constants.encode(entry.getKey());
			final ObjectId objectId = entry.getValue();
			objectsOfCommit.put(rawName, new InsertedObject(FileMode.REGULAR_FILE, objectId));
		}
		final ObjectId branchIdBefore = repository.resolve(branchRef);
		if (branchIdBefore != null) {
			@Cleanup
			final RevWalk revWalk = new RevWalk(repository);
			@Cleanup
			final TreeWalk treeWalk = new TreeWalk(repository);
			final RevCommit attcachementCommit = revWalk.parseCommit(branchIdBefore);
			treeWalk.addTree(attcachementCommit.getTree());
			while (treeWalk.next()) {
				final ObjectId objectId = treeWalk.getObjectId(0);
				final byte[] rawPath = treeWalk.getRawPath();
				objectsOfCommit.computeIfAbsent(rawPath, k -> new InsertedObject(treeWalk.getFileMode(0), objectId));
			}
		}
		final TreeFormatter treeFormatter = new TreeFormatter();
		for (final Entry<byte[], InsertedObject> commitEntry : objectsOfCommit.entrySet()) {
			final InsertedObject value = commitEntry.getValue();
			if (value != null) {
				treeFormatter.append(commitEntry.getKey(), value.getFileMode(), value.getId());
			}
		}
		final ObjectId treeObjectId = inserter.insert(treeFormatter);
		final CommitBuilder commitBuilder = createCommit(repository, branchIdBefore, treeObjectId);
		if (commitBuilder == null) {
			return Result.NO_CHANGE;
		}
		commitBuilder.setMessage(message);

		final ObjectId commitId = inserter.insert(commitBuilder);
		inserter.flush();
		final RefUpdate ru = repository.updateRef(branchRef);
		ru.setNewObjectId(commitId);
		ru.setRefLogMessage("branch updated " + message, true);
		if (branchIdBefore != null) {
			ru.setExpectedOldObjectId(branchIdBefore);
		} else {
			ru.setExpectedOldObjectId(ObjectId.zeroId());
		}
		final Result result = ru.update();
		log.info("Update result: " + result);
		return result;
	}

	private boolean updateFilesOfBranch(final Repository repository,
																			final String branch,
																			final Map<String, InsertHandler> updateHandlers,
																			final String message)	throws IOException,
																														AmbiguousObjectException,
																														IncorrectObjectTypeException,
																														MissingObjectException,
																														CorruptObjectException {
		final SortedMap<String, ObjectId> insertedObjects = insertObjects(repository.getObjectDatabase().newInserter(), updateHandlers);
		while (true) {
			final Result updateResult = updateBranch(branch, message, repository, insertedObjects);
			if (updateResult == Result.NEW || updateResult == Result.FAST_FORWARD) {
				return true;
			}
			if (updateResult == Result.REJECTED) {
				// repeat in case of conflict
				continue;
			}
			return false;
		}
	}

	private AttachementCache walkAttachement(final Repository repository, final String attachementRef) throws IOException, MissingObjectException, CorruptObjectException {
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		final ObjectId attachementRev = repository.resolve(attachementRef);
		String foundCommitId = null;
		final Map<String, ObjectId> existingAttachements = new HashMap<>();
		if (attachementRev != null) {
			@Cleanup
			final TreeWalk treeWalk = new TreeWalk(repository);
			final RevCommit attcachementCommit = revWalk.parseCommit(attachementRev);
			treeWalk.addTree(attcachementCommit.getTree());
			while (treeWalk.next()) {
				final ObjectId objectId = treeWalk.getObjectId(0);
				final String pathString = treeWalk.getPathString();
				if (pathString.equals(COMMIT_ID_FILE)) {
					final ObjectLoader commitIdLoader = repository.open(objectId);
					if (commitIdLoader.getSize() > 100) {
						continue;
					}
					foundCommitId = new String(commitIdLoader.getBytes());
					continue;
				}
				existingAttachements.put(pathString, objectId);
			}
		}
		return new AttachementCache(foundCommitId, existingAttachements, attachementRev);
	}
}
