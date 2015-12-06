package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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

import javax.annotation.PostConstruct;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.model.AlbumCache;
import ch.bergturbenthal.raoa.server.spring.model.AlbumData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumMetadata;
import ch.bergturbenthal.raoa.server.spring.model.AttachementCache;
import ch.bergturbenthal.raoa.server.spring.model.BranchState;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator.ObjectLoaderLookup;
import ch.bergturbenthal.raoa.server.spring.service.HashGenerator;
import ch.bergturbenthal.raoa.server.spring.util.FindGitDirWalker;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BareGitAlbumAccess implements AlbumAccess {
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
	private static final String COMMIT_ID_FILE = "commit-id";
	private static final String MASTER_REF = _REFS_HEADS + "master";

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

	private void enqueueThumbnailUpdate(final String repoName, final AlbumData albumData, final AttachementGenerator attachementGenerator) {

		final String attachementRef = attachementGenerator.attachementType();
		try {
			attachementGeneratorExecutors.get(attachementGenerator).submit(new Runnable() {

				private boolean flush(final Map<String, ObjectId> pendingObjects, final AlbumData albumData, final ObjectId lastThumbnailCommit) throws IOException {
					final Repository repository = albumData.getAlbumRepository();
					final ObjectId thumbnailRefBefore = repository.resolve(_REFS_HEADS + attachementRef);
					final TreeFormatter thumbnailTreeFormatter = new TreeFormatter();
					for (final Entry<String, ObjectId> fileEntry : pendingObjects.entrySet()) {
						thumbnailTreeFormatter.append(fileEntry.getKey(), FileMode.REGULAR_FILE, fileEntry.getValue());
					}
					final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
					final ObjectId treeObjectId = inserter.insert(thumbnailTreeFormatter);
					final CommitBuilder commitBuilder = createCommit(repository, thumbnailRefBefore, treeObjectId);
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
						final AlbumCache albumEntries = getAlbumEntries(albumData);
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
						final Map<String, ObjectId> entriesOfCommit = new HashMap<String, ObjectId>();
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

	private AlbumCache getAlbumEntries(final AlbumData albumData) throws IOException {
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
				updateAlbumState(albumData);
			}
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
			data.getAttachementCaches().put(attachementRef, new WeakReference<AttachementCache>(createdResult));
			return createdResult;
		}
	}

	@PostConstruct
	public void initExecutors() {
		for (final AttachementGenerator attachementGenerator : attachementGenerators) {
			attachementGeneratorExecutors.put(attachementGenerator, new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
		}
	}

	@Override
	public void listAlbums() {
		// TODO Auto-generated method stub

	}

	private void notifyFoundAlbum(final AlbumData albumData) {
		// TODO Auto-generated method stub

	}

	private void notifyModifiedAlbum(final BranchState branchStateBefore, final AlbumData albumData) throws IOException {
		final AlbumCache newAlbumEntries = getAlbumEntries(albumData);
		// TODO Auto-generated method stub

	}

	private void notifyRemovedAlbum(final AlbumData removedAlbum) {
		// TODO Auto-generated method stub

	}

	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000 * 10)
	public void refreshAlbums() {

		final String albumBaseDir = configuration.getAlbumBaseDir();
		updateAlbumList(albumBaseDir);
		for (final Entry<String, AlbumData> albumEntry : albums.entrySet()) {
			final AlbumData albumData = albumEntry.getValue();
			try {
				// final long startAlbumLoad = System.currentTimeMillis();
				final AlbumCache albumState = getAlbumEntries(albumData);
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

	private void updateAlbumList(final String albumBaseDir) {
		if (albumBaseDir != null) {
			final File albumBaseFile = new File(albumBaseDir);
			final FileSystem fs = FileSystems.getDefault();
			final Path albumBasePath = fs.getPath(albumBaseFile.getPath());
			try {
				final FindGitDirWalker gitDirWalker = new FindGitDirWalker();
				final HashSet<String> remainingAlbums = new HashSet<>(albums.keySet());
				for (final File foundDir : gitDirWalker.findGitDirs(albumBaseFile)) {
					final Path albumPath = fs.getPath(foundDir.getPath());
					final String pathName = relative(albumBasePath, albumPath);
					try {
						final Repository repository = new FileRepositoryBuilder().readEnvironment().setGitDir(foundDir).setMustExist(true).setBare().build();
						final AlbumData albumData = AlbumData.builder().fullAlbumName(pathName).albumRepository(repository).build();
						final AlbumCache albumEntries = getAlbumEntries(albumData);
						if (albumEntries == null) {
							continue;
						}
						final AlbumMetadata albumMetadata = albumEntries.getAlbumMetadata();
						final String albumKey;
						if (albumMetadata == null) {
							final AlbumMetadata newMetadata = new AlbumMetadata();
							newMetadata.setAlbumId(hashGenerator.generateHash(pathName));
							newMetadata.setAlbumTitle(foundDir.getName());
							final Map<String, AlbumEntryData> entries = albumEntries.getEntries();
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
								final Map<String, AlbumEntryData> entries = albumEntries.getEntries();
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
					}
				}
				for (final String remainingAlbum : remainingAlbums) {
					final AlbumData removedAlbum = albums.remove(remainingAlbum);
					if (removedAlbum != null) {
						notifyRemovedAlbum(removedAlbum);
					}
				}
			} catch (final IOException e) {
				log.error("Cannot load album list", e);
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
		final AlbumCache albumState = getAlbumEntries(albumData);
		if (albumState == null) {
			return false;
		}
		final Repository repository = albumData.getAlbumRepository();
		final Map<String, ObjectId> files = albumState.getFiles();
		final ObjectId objectIdBefore = files.get(RAOA_JSON);
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
		final ObjectDatabase objectDatabase = repository.getObjectDatabase();
		final ObjectInserter inserter = objectDatabase.newInserter();
		final byte[] newValue = ALBUM_METADATA_WRITER.writeValueAsBytes(metadataAfter);
		final ObjectId newObject = inserter.insert(Constants.OBJ_BLOB, newValue);
		if (Objects.equals(objectIdBefore, newObject)) {
			// no update needed
			return true;
		}
		final TreeFormatter commitTreeFormatter = new TreeFormatter();
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);

		final RevCommit commit = revWalk.parseCommit(albumState.getLastCommit());
		final RevTree tree = commit.getTree();
		treeWalk.addTree(tree);
		boolean updatedFile = false;
		while (treeWalk.next()) {
			final String name = treeWalk.getPathString();
			if (name.equals(RAOA_JSON)) {
				commitTreeFormatter.append(RAOA_JSON, FileMode.REGULAR_FILE, newObject);
				updatedFile = true;
				continue;
			}
			commitTreeFormatter.append(name, treeWalk.getFileMode(0), treeWalk.getObjectId(0));
		}
		if (!updatedFile) {
			commitTreeFormatter.append(RAOA_JSON, FileMode.REGULAR_FILE, newObject);
		}
		final ObjectId treeId = inserter.insert(commitTreeFormatter);
		final CommitBuilder newCommit = createCommit(repository, albumState.getLastCommit(), treeId);
		newCommit.setMessage("metadata updated");
		final ObjectId commitId = inserter.insert(newCommit);
		inserter.flush();
		final RefUpdate ru = repository.updateRef(MASTER_REF);
		ru.setExpectedOldObjectId(albumState.getLastCommit());
		ru.setNewObjectId(commitId);
		ru.setRefLogMessage("file stored", false);
		final Result result = ru.update();
		log.info("Metadata update result: " + result);
		albumData.getAlbumCacheReference().set(null);
		return Arrays.asList(Result.FAST_FORWARD, Result.NEW).contains(result);

	}

	private boolean updateAlbumMeta(final String albumKey, final AlbumMetadata metadataBefore, final AlbumMetadata metadataAfter) throws IOException {
		final AlbumData albumData = albums.get(albumKey);
		if (albumData == null) {
			return false;
		}
		return updateAlbumMeta(albumData, metadataBefore, metadataAfter);
	}

	protected void updateAlbumState(final AlbumData albumData) throws IOException {

		final Map<String, AlbumEntryData> albumEntries = new HashMap<>();
		final Repository repository = albumData.getAlbumRepository();
		final ObjectId masterRef = repository.resolve(MASTER_REF);
		if (masterRef == null) {
			log.error("No Master in " + repository);
			return;
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
		albumData.getAlbumCacheReference().set(new WeakReference<AlbumCache>(new AlbumCache(metadata, albumEntries, fileEntries, masterRef)));
		final AtomicReference<BranchState> currentStateRef = albumData.getCurrentState();
		final BranchState branchStateBefore = currentStateRef.get();
		currentStateRef.set(BranchState.builder().lastCommit(masterRef).lastRefreshTime(System.currentTimeMillis()).build());
		if (branchStateBefore == null) {
			notifyFoundAlbum(albumData);
		} else if (!branchStateBefore.getLastCommit().equals(masterRef)) {
			notifyModifiedAlbum(branchStateBefore, albumData);
		}
	}

	private AttachementCache walkAttachement(final Repository repository, final String attachementRef) throws IOException, MissingObjectException, CorruptObjectException {
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		final ObjectId attachementRev = repository.resolve(attachementRef);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);
		String foundCommitId = null;
		final Map<String, ObjectId> existingAttachements = new HashMap<>();
		if (attachementRev != null) {
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
