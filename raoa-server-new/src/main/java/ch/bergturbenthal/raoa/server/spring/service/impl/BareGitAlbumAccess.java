package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

import javax.annotation.PostConstruct;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
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

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.model.AlbumData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryData;
import ch.bergturbenthal.raoa.server.spring.model.AlbumState;
import ch.bergturbenthal.raoa.server.spring.model.AttachementState;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator;
import ch.bergturbenthal.raoa.server.spring.service.AttachementGenerator.ObjectLoaderLookup;
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

	private static final String _REFS_HEADS = "refs/heads/";
	private static final String COMMIT_ID_FILE = "commit-id";
	private static final String MASTER_REF = _REFS_HEADS + "master";

	public static <R> Future<R> completedFuture(final R value) {
		return new CompletedFuture<R>(value);
	}

	private final ConcurrentMap<File, AlbumData> albums = new ConcurrentHashMap<>();
	private final Map<AttachementGenerator, ExecutorService> attachementGeneratorExecutors = new HashMap<AttachementGenerator, ExecutorService>();
	@Autowired
	private List<AttachementGenerator> attachementGenerators;
	@Autowired
	private ServerConfiguration configuration;

	private void enqueueThumbnailUpdate(final AlbumData albumData, final AttachementGenerator attachementGenerator) {
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
					final CommitBuilder commitBuilder = new CommitBuilder();
					if (thumbnailRefBefore != null) {
						try (RevWalk walk = new RevWalk(repository)) {
							final RevCommit commit = walk.parseCommit(thumbnailRefBefore);
							final RevTree tree = commit.getTree();
							if (tree.getId().equals(treeObjectId)) {
								log.warn("No change -> Skip commit");
								return true;
							}
						}
						commitBuilder.setParentId(thumbnailRefBefore);
					}
					commitBuilder.setTreeId(treeObjectId);
					final PersonIdent author = new PersonIdent("Server ", "dummy@none");
					commitBuilder.setAuthor(author);
					commitBuilder.setCommitter(author);
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
					final Repository repository = albumData.getAlbumRepository();
					try {
						final AlbumState albumEntries = getAlbumEntries(albumData);
						if (albumEntries == null) {
							return;
						}
						final AttachementState existingAttachementState = getAttachementState(albumData, attachementRef);
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
			log.info("queue full -> skip insertion");
		}
	}

	private AlbumState getAlbumEntries(final AlbumData albumData) throws IOException {
		if (albumData == null) {
			return null;
		}
		synchronized (albumData) {
			final WeakReference<AlbumState> albumStateReference = albumData.getAlbumStateReference();
			if (albumStateReference != null) {
				final AlbumState albumState = albumStateReference.get();
				if (albumState != null) {
					if ((System.currentTimeMillis() - albumState.getCreateTime()) < 10 * 1000) {
						return albumState;
					}
				}
			}
			final AlbumState albumState = loadAlbumEntries(albumData);
			albumData.setAlbumStateReference(new WeakReference<AlbumState>(albumState));
			return albumState;
		}
	}

	private AttachementState getAttachementState(final AlbumData data, final String attachementType) throws IOException {
		if (data == null) {
			return null;
		}
		final String attachementRef = _REFS_HEADS + attachementType;
		synchronized (data) {
			final WeakReference<AttachementState> thumbnailStateReference = data.getAttachementStates().get(attachementRef);
			if (thumbnailStateReference != null) {
				final AttachementState thumbnailState = thumbnailStateReference.get();
				if (thumbnailState != null) {
					if ((System.currentTimeMillis() - thumbnailState.getCreateTime()) < 10 * 1000) {
						return thumbnailState;
					}
				}
			}
			final AttachementState createdResult = walkAttachement(data.getAlbumRepository(), attachementRef);
			data.getAttachementStates().put(attachementRef, thumbnailStateReference);
			return createdResult;
		}
	}

	@PostConstruct
	public void initExecutors() {
		for (final AttachementGenerator attachementGenerator : attachementGenerators) {
			attachementGeneratorExecutors.put(attachementGenerator, new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
		}
	}

	private AlbumState loadAlbumEntries(final AlbumData albumData) throws IOException {
		final Map<String, AlbumEntryData> albumEntries = new HashMap<>();
		final Repository repository = albumData.getAlbumRepository();
		final ObjectId masterRef = repository.resolve(MASTER_REF);
		if (masterRef == null) {
			log.error("No Master in " + repository);
			return null;
		}
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);

		final RevCommit commit = revWalk.parseCommit(masterRef);
		final RevTree tree = commit.getTree();
		treeWalk.addTree(tree);

		final Map<String, ObjectId> fileEntries = new HashMap<String, ObjectId>();
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
			final AttachementState attachementState = getAttachementState(albumData, attachementGenerator.attachementType());
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

		return new AlbumState(albumEntries, fileEntries, masterRef);
	}

	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000 * 10)
	public void refreshAlbums() {

		final String albumBaseDir = configuration.getAlbumBaseDir();
		updateAlbumList(albumBaseDir);
		for (final AlbumData albumData : albums.values()) {
			try {
				// final long startAlbumLoad = System.currentTimeMillis();
				final AlbumState albumState = getAlbumEntries(albumData);
				final ObjectId masterRef = albumState.getLastCommit();
				// log.info("Loaded directory of " + albumEntries.size() + " entries in " + (System.currentTimeMillis() - startAlbumLoad) + " ms");
				for (final AttachementGenerator attachementGenerator : attachementGenerators) {
					final AttachementState attachementState = getAttachementState(albumData, attachementGenerator.attachementType());
					final String foundCommitId = attachementState.getCommitId();
					if (foundCommitId == null || !masterRef.getName().equals(foundCommitId)) {
						enqueueThumbnailUpdate(albumData, attachementGenerator);
					}
				}
			} catch (final IOException e) {
				log.error("Cannot load data from " + albumData.getAlbumRepository(), e);
			}
		}
	}

	private void updateAlbumList(final String albumBaseDir) {
		if (albumBaseDir != null) {
			final File albumBaseFile = new File(albumBaseDir);
			try {
				final FindGitDirWalker gitDirWalker = new FindGitDirWalker();
				final HashSet<File> remainingAlbums = new HashSet<>(albums.keySet());
				for (final File foundDir : gitDirWalker.findGitDirs(albumBaseFile)) {
					try {
						if (!albums.containsKey(foundDir)) {
							final AlbumData albumData = new AlbumData();
							final Repository repository = new FileRepositoryBuilder().readEnvironment().setGitDir(foundDir).setMustExist(true).setBare().build();
							albumData.setAlbumRepository(repository);
							albums.putIfAbsent(foundDir, albumData);
						}
						remainingAlbums.remove(foundDir);
					} catch (final IOException e) {
						log.error("Cannot load repository " + foundDir, e);
					}
				}
				albums.keySet().removeAll(remainingAlbums);
			} catch (final IOException e) {
				log.error("Cannot load album list", e);
			}
		} else {
			log.error("no base dir configured");
		}
	}

	private AttachementState walkAttachement(final Repository repository, final String attachementRef) throws IOException, MissingObjectException, CorruptObjectException {
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
		return new AttachementState(foundCommitId, existingAttachements, attachementRev);
	}
}
