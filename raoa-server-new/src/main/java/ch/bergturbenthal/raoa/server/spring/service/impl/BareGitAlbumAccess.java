package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

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
import ch.bergturbenthal.raoa.server.spring.model.ThumbnailState;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;
import ch.bergturbenthal.raoa.server.spring.util.FindGitDirWalker;

@Slf4j
@Service
public class BareGitAlbumAccess implements AlbumAccess {
	private static final String COMMIT_ID_FILE = "commit-id";
	private static final String MASTER_REF = "refs/heads/master";
	private static final String THUMBNAILS_REF = "refs/heads/thumbnails";
	private final ConcurrentMap<File, AlbumData> albums = new ConcurrentHashMap<>();
	@Autowired
	private ServerConfiguration configuration;
	@Autowired
	private List<ThumbnailMaker> thumbnailMakers;

	private final ExecutorService thumbnailUpdateExecutor = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100));

	private void enqueueThumbnailUpdate(final AlbumData albumData) {
		try {
			thumbnailUpdateExecutor.submit(new Runnable() {

				private boolean flush(final Map<String, ObjectId> pendingObjects, final AlbumData albumData, final ObjectId lastThumbnailCommit) throws IOException {
					final Repository repository = albumData.getAlbumRepository();
					final ObjectId thumbnailRefBefore = repository.resolve(THUMBNAILS_REF);
					final TreeFormatter thumbnailTreeFormatter = new TreeFormatter();
					for (final Entry<String, ObjectId> fileEntry : pendingObjects.entrySet()) {
						thumbnailTreeFormatter.append(fileEntry.getKey(), FileMode.REGULAR_FILE, fileEntry.getValue());
					}
					final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
					final ObjectId treeObjectId = inserter.insert(thumbnailTreeFormatter);
					final CommitBuilder commitBuilder = new CommitBuilder();
					if (thumbnailRefBefore != null) {
						commitBuilder.setParentId(thumbnailRefBefore);
					}
					commitBuilder.setTreeId(treeObjectId);
					final PersonIdent author = new PersonIdent("Server ", "dummy@none");
					commitBuilder.setAuthor(author);
					commitBuilder.setCommitter(author);
					final ObjectId commitId = inserter.insert(commitBuilder);
					inserter.flush();
					final RefUpdate ru = repository.updateRef(THUMBNAILS_REF);
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

				private boolean hasThumbnail(final AlbumData albumData, final String id) throws IOException {
					final ThumbnailState thumbnailState = getThumbnailState(albumData);
					return thumbnailState.getExistingThumbnails().keySet().contains(id);
				}

				@Override
				public void run() {
					final Repository repository = albumData.getAlbumRepository();
					final Map<ThumbnailMaker, ExecutorService> executors = new HashMap<ThumbnailMaker, ExecutorService>();
					try {
						final AlbumState albumEntries = getAlbumEntries(albumData);
						if (albumEntries == null) {
							return;
						}
						final ThumbnailState thumbnailState = getThumbnailState(albumData);
						if (thumbnailState != null) {
							if (albumEntries.getLastCommit().getName().equals(thumbnailState.getCommitId())) {
								// all thumbnails are up to date
								return;
							}
						}
						final ObjectId lastThumbnailCommit = thumbnailState.getLastThumbnailCommit();
						final AtomicLong lastFlushTime = new AtomicLong(System.currentTimeMillis());
						final Map<String, ObjectId> pendingObjects = new HashMap<>();
						final List<Future<?>> pendingThumbnails = new ArrayList<Future<?>>();
						for (final Entry<String, AlbumEntryData> entry : albumEntries.getEntries().entrySet()) {
							final String originalId = entry.getValue().getOriginalFileId().name();
							final ObjectId existingThumbnail = getThumbnailState(albumData).getExistingThumbnails().get(originalId);
							if (existingThumbnail != null) {
								synchronized (pendingObjects) {
									pendingObjects.put(originalId, existingThumbnail);
									// thumbnailTreeFormatter.append(originalId, FileMode.REGULAR_FILE, existingThumbnail);
								}
								continue;
							}
							final String originalFilename = entry.getKey();
							final ThumbnailMaker maker = findThumbnailMaker(originalFilename);
							if (maker == null) {
								continue;
							}
							final int lastPt = originalFilename.lastIndexOf('.');
							if (lastPt < 2) {
								continue;
							}
							final String suffix = originalFilename.substring(lastPt);
							if (!executors.containsKey(maker)) {
								executors.put(maker, maker.createExecutorservice());
							}
							final ExecutorService executorService = executors.get(maker);
							pendingThumbnails.add(executorService.submit(new Runnable() {

								@Override
								public void run() {
									try {
										final AlbumState albumEntries = getAlbumEntries(albumData);
										if (albumEntries == null) {
											return;
										}
										final AlbumEntryData albumEntryData = albumEntries.getEntries().get(originalFilename);
										if (albumEntryData == null) {
											return;
										}

										final File tempInFile = File.createTempFile("thumbnail-in", suffix);
										final File tempOutFile = File.createTempFile("thumbnail-out", suffix);
										try {
											final ObjectLoader objectLoader = repository.open(albumEntryData.getOriginalFileId());
											try (FileOutputStream inFileOS = new FileOutputStream(tempInFile)) {
												objectLoader.copyTo(inFileOS);
											}
											maker.makeThumbnail(tempInFile, tempOutFile, tempOutFile.getParentFile());

											final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
											final ObjectId objectId;
											try (FileInputStream outFileIs = new FileInputStream(tempOutFile)) {
												objectId = inserter.insert(Constants.OBJ_BLOB, tempOutFile.length(), outFileIs);
											}
											inserter.flush();
											synchronized (pendingObjects) {
												pendingObjects.put(albumEntryData.getOriginalFileId().getName(), objectId);
												if ((System.currentTimeMillis() - lastFlushTime.get()) > 20 * 1000) {
													flush(pendingObjects, albumData, lastThumbnailCommit);
													lastFlushTime.set(System.currentTimeMillis());
												}
											}
										} finally {
											tempInFile.delete();
											tempOutFile.delete();
										}
									} catch (final IOException e) {
										throw new RuntimeException("Cannot create thumbnail for " + originalFilename, e);
									}
								}

							}));
						}
						for (final Future<?> future : pendingThumbnails) {
							future.get();
						}
						final ObjectInserter inserter = repository.getObjectDatabase().newInserter();
						final ObjectId commitIdFile = inserter.insert(Constants.OBJ_BLOB, albumEntries.getLastCommit().getName().getBytes());
						pendingObjects.put(COMMIT_ID_FILE, commitIdFile);
						inserter.flush();
						flush(pendingObjects, albumData, lastThumbnailCommit);

					} catch (final IOException | InterruptedException | ExecutionException e) {
						log.error("cannot load thumbnails of " + repository, e);
					} finally {
						for (final ExecutorService service : executors.values()) {
							service.shutdown();
						}
					}

				}

			});
		} catch (final RejectedExecutionException e) {
			log.info("queue full -> skip insertion");
		}
	}

	private ThumbnailMaker findThumbnailMaker(final String filename) {
		for (final ThumbnailMaker thumbnailMaker : thumbnailMakers) {
			if (thumbnailMaker.canMakeThumbnail(filename)) {
				return thumbnailMaker;
			}
		}
		return null;
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

	private ThumbnailState getThumbnailState(final AlbumData data) throws IOException {
		if (data == null) {
			return null;
		}
		synchronized (data) {
			final WeakReference<ThumbnailState> thumbnailStateReference = data.getThumbnailStateReference();
			if (thumbnailStateReference != null) {
				final ThumbnailState thumbnailState = thumbnailStateReference.get();
				if (thumbnailState != null) {
					if ((System.currentTimeMillis() - thumbnailState.getCreateTime()) < 10 * 1000) {
						return thumbnailState;
					}
				}
			}
			final ThumbnailState createdResult = walkThumbnails(data.getAlbumRepository());
			data.setThumbnailStateReference(new WeakReference<ThumbnailState>(createdResult));
			return createdResult;
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
		final ThumbnailState thumbnailState = getThumbnailState(albumData);
		final Map<String, ObjectId> existingThumbnails = thumbnailState.getExistingThumbnails();
		while (treeWalk.next()) {
			if (treeWalk.isSubtree()) {
				continue;
			}
			final ObjectId objectId = treeWalk.getObjectId(0);
			final String pathString = treeWalk.getPathString();
			albumEntries.put(pathString, AlbumEntryData.builder().originalFileId(objectId).thumbailId(existingThumbnails.get(objectId.getName())).build());
		}

		return new AlbumState(albumEntries, masterRef);
	}

	@PostConstruct
	@Scheduled(fixedDelay = 1000 * 60 * 10, initialDelay = 1000 * 60 * 5)
	public void refreshAlbums() {

		final String albumBaseDir = configuration.getAlbumBaseDir();
		updateAlbumList(albumBaseDir);
		for (final AlbumData albumData : albums.values()) {
			try {
				final long startAlbumLoad = System.currentTimeMillis();
				final ThumbnailState thumbnailState = getThumbnailState(albumData);
				final AlbumState albumState = getAlbumEntries(albumData);
				final Map<String, AlbumEntryData> albumEntries = albumState.getEntries();
				final ObjectId masterRef = albumState.getLastCommit();
				final String foundCommitId = thumbnailState.getCommitId();
				// log.info("Loaded directory of " + albumEntries.size() + " entries in " + (System.currentTimeMillis() - startAlbumLoad) + " ms");
				if (foundCommitId == null || !masterRef.getName().equals(foundCommitId)) {
					enqueueThumbnailUpdate(albumData);
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

	private ThumbnailState walkThumbnails(final Repository repository) throws IOException, MissingObjectException, CorruptObjectException {
		@Cleanup
		final RevWalk revWalk = new RevWalk(repository);
		final ObjectId thumbnailsRev = repository.resolve(THUMBNAILS_REF);
		@Cleanup
		final TreeWalk treeWalk = new TreeWalk(repository);
		String foundCommitId = null;
		final Map<String, ObjectId> existingThumbnails = new HashMap<>();
		if (thumbnailsRev != null) {
			final RevCommit thumbnailsCommit = revWalk.parseCommit(thumbnailsRev);
			treeWalk.addTree(thumbnailsCommit.getTree());
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
				existingThumbnails.put(pathString, objectId);
			}
		}
		return new ThumbnailState(foundCommitId, existingThumbnails, thumbnailsRev);
	}
}
