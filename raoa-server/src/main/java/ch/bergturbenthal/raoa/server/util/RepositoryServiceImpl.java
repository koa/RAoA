package ch.bergturbenthal.raoa.server.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.FS;
import org.springframework.beans.factory.annotation.Autowired;

import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.IssueType;
import ch.bergturbenthal.raoa.server.model.BranchConflictEntry;
import ch.bergturbenthal.raoa.server.model.ConflictEntry;
import ch.bergturbenthal.raoa.server.model.FileConflictCache;
import ch.bergturbenthal.raoa.server.model.FileConflictEntry;
import ch.bergturbenthal.raoa.server.state.CloseableProgressMonitor;
import ch.bergturbenthal.raoa.server.state.StateManager;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
public class RepositoryServiceImpl implements RepositoryService {
	private static final class InfiniteCountIterator implements Iterator<String> {
		private int i = 0;

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public String next() {
			return Integer.toString(++i);
		}

		@Override
		public void remove() {
		}
	}

	private static final String CONFLICT_BRANCH_PREFIX = "refs/heads/conflict/";

	private static final String MASTER_REF = "refs/heads/master";
	private static final RefSpec MASTER_REF_SPEC = new RefSpec(MASTER_REF);

	private static final String NOTES_CONFLICT_PREFIX = "refs/notes/conflicts";
	private final ObjectMapper mapper = new ObjectMapper();
	private final Set<MergeStatus> modifiedMergeStates = new HashSet<MergeStatus>(Arrays.asList(MergeStatus.FAST_FORWARD,
																																															MergeStatus.FAST_FORWARD_SQUASHED,
																																															MergeStatus.MERGED,
																																															MergeStatus.MERGED_SQUASHED));

	@Autowired
	private StateManager stateManager;

	@Override
	public void checkoutMaster(final Git repository) {
		try {
			final String fullBranch = repository.getRepository().getFullBranch();
			if ("refs/heads/master".equals(fullBranch)) {
				return;
			}
			final Ref ref = repository.checkout().setName(MASTER_REF).call();
			log.debug("Checked out Reference " + ref);
		} catch (final GitAPIException | IOException e) {
			log.error("cannot checkout master", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ch.bergturbenthal.image.server.util.RepositoryService#cleanOldConflicts (org.eclipse.jgit.api.Git)
	 */
	@Override
	public void cleanOldConflicts(final Git git) {
		// remove all fully-merged conflict-branches
		for (final Entry<String, Ref> refEntry : collectConflictBranches(git)) {
			try {
				git.branchDelete().setBranchNames(refEntry.getKey()).call();
			} catch (final NotMergedException ex) {
				// skips branch that is not fully merged
			} catch (final CannotDeleteCurrentBranchException e) {
				// skips current branch
			} catch (final GitAPIException e) {
				throw new RuntimeException("Error cleaning up conflict-branches", e);
			}
		}
		// final Collection<ConflictEntry> conflicts = describeConflicts(git);
		// if (conflicts.isEmpty())
		// return;
		// logger.info("Conflicts for " + git.getRepository());
		// for (final ConflictEntry conflictEntry : conflicts) {
		// logger.info("  - " + conflictEntry);
		// }
	}

	@Override
	public int countCommits(final Git git) {
		try {
			int commitCount = 0;
			for (final RevCommit commit : git.log().call()) {
				commitCount += 1;
			}
			return commitCount;
		} catch (final GitAPIException e) {
			log.warn("Cannot count commits on " + git, e);
			return 0;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ch.bergturbenthal.image.server.util.RepositoryService#describeConflicts (org.eclipse.jgit.api.Git)
	 */
	@Override
	public Collection<ConflictEntry> describeConflicts(final Git git, final File conflictFile) {
		final Map<AnyObjectId, ObjectId> notes = new HashMap<AnyObjectId, ObjectId>();
		try {
			final List<Note> foundNotes = git.notesList().setNotesRef(NOTES_CONFLICT_PREFIX).call();
			for (final Note note : foundNotes) {
				notes.put(note, note.getData());

			}
		} catch (final GitAPIException e) {
			log.error("Cannot read notes from " + git.getRepository(), e);
		}
		final FileConflictCache conflictCache = loadConflictCache(conflictFile);
		final Map<String, BranchConflictEntry> oldConflicts = conflictCache.getConflicts();
		final Map<String, BranchConflictEntry> newConflicts = new HashMap<>();
		final ArrayList<ConflictEntry> ret = new ArrayList<ConflictEntry>();
		for (final Entry<String, Ref> entry : collectConflictBranches(git)) {
			final String branchName = entry.getKey();
			try {
				final BranchConflictEntry cachedConflicts = oldConflicts.get(branchName);
				final BranchConflictEntry conflictData;
				if (cachedConflicts == null) {
					final ObjectReader objectReader = git.getRepository().newObjectReader();
					final CanonicalTreeParser branchTree;
					final RevCommit branchCommit;
					try {
						final RevWalk revWalk = new RevWalk(objectReader);
						try {
							branchCommit = revWalk.parseCommit(entry.getValue().getObjectId());
							branchTree = new CanonicalTreeParser(null, objectReader, branchCommit.getTree().getId());
						} finally {
							revWalk.dispose();
						}
					} finally {
						objectReader.release();
					}
					final List<DiffEntry> diffs = git.diff().setOldTree(branchTree).setShowNameAndStatusOnly(true).call();
					final ObjectId attachedNote = notes.get(entry.getValue().getObjectId());
					final ConflictMeta conflictMeta;
					if (attachedNote != null) {

						final ObjectLoader noteObjectLoader = git.getRepository().getObjectDatabase().open(attachedNote);
						final byte[] note = IOUtils.toByteArray(noteObjectLoader.openStream());
						conflictMeta = mapper.readValue(note, ConflictMeta.class);
					} else {
						conflictMeta = null;
					}
					final Collection<FileConflictEntry> conflictEntries = new ArrayList<>(diffs.size());
					for (final DiffEntry diffEntry : diffs) {
						conflictEntries.add(new FileConflictEntry(diffEntry));
					}
					conflictData = new BranchConflictEntry();
					conflictData.setBranchCommit(branchCommit.getName());
					conflictData.setFileEntries(conflictEntries);
					conflictData.setConflictMeta(conflictMeta);
				} else {
					conflictData = cachedConflicts;
				}
				newConflicts.put(branchName, conflictData);
				final ConflictEntry conflictEntry = new ConflictEntry();
				conflictEntry.setBranch(branchName);
				conflictEntry.setDiffs(conflictData.getFileEntries());
				conflictEntry.setMeta(conflictData.getConflictMeta());
				final Map<IssueResolveAction, Runnable> actions = new HashMap<>();
				actions.put(IssueResolveAction.IGNORE_OTHER, makeIgnoreOtherRunnable(git, conflictData.getBranchCommit()));
				actions.put(IssueResolveAction.IGNORE_THIS, makeIgnoreThisRunnable(git, conflictData.getBranchCommit()));
				conflictEntry.setResolveActions(actions);
				ret.add(conflictEntry);
			} catch (final Throwable e) {
				throw new RuntimeException("Cannot parse branch " + branchName, e);
			}
		}
		if (newConflicts.isEmpty()) {
			conflictFile.delete();
		} else if (!newConflicts.equals(oldConflicts)) {
			final FileConflictCache cacheToStore = new FileConflictCache();
			cacheToStore.setConflicts(newConflicts);
			try {
				mapper.writerFor(FileConflictCache.class).writeValue(conflictFile, cacheToStore);
			} catch (final IOException e) {
				log.error("Cannot write cache to " + conflictFile, e);
			}
		}
		return ret;
	}

	@Override
	public boolean isCurrentMaster(final Git repository) {
		try {
			final String branchName = repository.getRepository().getFullBranch();
			return branchName != null && branchName.equals(MASTER_REF);
		} catch (final IOException e) {
			log.error("cannot detect current branch of " + repository, e);
		}
		return false;
	}

	@Override
	public boolean isRepository(final File directory, final boolean bare) {

		try {
			final FS fs = FS.DETECTED;

			final RepositoryCache.FileKey key = RepositoryCache.FileKey.lenient(directory, fs);
			Repository repository;
			repository = new RepositoryBuilder().setFS(fs).setGitDir(key.getFile()).setMustExist(false).build();

			return repository.getObjectDatabase().exists() && repository.isBare() == bare;
		} catch (final IOException e) {
			log.debug("Cannot find repository at " + directory, e);
			return false;
		}
	}

	@Override
	public boolean pull(final Git localRepo, final String remoteUri, final String serverName) {
		try {
			@Cleanup
			final CloseableProgressMonitor monitor = stateManager.makeProgressMonitor();
			try {
				localRepo.fetch().setRemote(remoteUri).setRefSpecs(MASTER_REF_SPEC).setProgressMonitor(monitor).call();
			} catch (final GitAPIException ex) {
				log.warn("Cannte fetch head from " + remoteUri, ex);
			}
			final Repository repository = localRepo.getRepository();
			final Ref fetchHead = repository.getRef("FETCH_HEAD");
			final Ref headBefore = repository.getRef(MASTER_REF);
			final MergeResult mergeResult = localRepo.merge().include(fetchHead).call();
			final MergeStatus mergeStatus = mergeResult.getMergeStatus();
			if (!mergeStatus.isSuccessful()) {
				// reset master to old state
				localRepo.reset().setRef(headBefore.getObjectId().getName()).setMode(ResetType.HARD).call();
				boolean alreadyBranch = false;
				for (final Entry<String, Ref> refEntry : repository.getAllRefs().entrySet()) {
					if (refEntry.getValue().getObjectId().equals(fetchHead.getObjectId())) {
						alreadyBranch = true;
					}
				}
				if (!alreadyBranch) {
					// make a conflict branch with taken version
					final String nextConflictId = findNextFreeConflictBranch(localRepo, serverName, new InfiniteCountIterator());
					final Ref newBranch = localRepo.branchCreate().setStartPoint(fetchHead.getObjectId().getName()).setName("conflict/" + nextConflictId).call();
					final ConflictMeta conflictMeta = new ConflictMeta();
					conflictMeta.setRemoteUri(remoteUri);
					conflictMeta.setServer(serverName);
					conflictMeta.setConflictDate(new Date());
					final String conflictMetaJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conflictMeta);
					final RevWalk revWalk = new RevWalk(localRepo.getRepository());
					try {
						final RevObject id = revWalk.lookupCommit(newBranch.getObjectId());
						localRepo.notesAdd().setObjectId(id).setNotesRef(NOTES_CONFLICT_PREFIX).setMessage(conflictMetaJson).call();
					} finally {
						revWalk.release();
					}
				}
			}
			cleanOldConflicts(localRepo);
			return modifiedMergeStates.contains(mergeStatus);
		} catch (final GitAPIException e) {
			throw new RuntimeException("Cannot sync local repo " + localRepo.getRepository().toString() + " with " + remoteUri, e);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot sync local repo " + localRepo.getRepository().toString() + " with " + remoteUri, e);
		}
	}

	@Override
	public boolean sync(final Git localRepository, final File externalDir, final String localName, final String remoteName, final boolean bare, final ReadWriteLock rwLock) {
		try {
			final Git externalRepository;
			if (!externalDir.exists()) {
				externalRepository = Git.init().setBare(bare).setDirectory(externalDir).call();
				final StoredConfig config = externalRepository.getRepository().getConfig();
				config.setBoolean("pack", null, "deltacompression", false);
				config.save();
			} else {
				externalRepository = Git.open(externalDir);
			}
			final Lock writeLock = rwLock.writeLock();
			writeLock.lock();
			try {
				if (!localRepository.status().call().isClean()) {
					localRepository.add().addFilepattern(".").call();
					localRepository.commit().setMessage("Commit for Synchronisation").call();
				}
			} finally {
				writeLock.unlock();
			}
			if (!bare && !externalRepository.status().call().isClean()) {
				externalRepository.add().addFilepattern(".").call();
				externalRepository.commit().setMessage("Commit for Synchronisation").call();
			}
			final boolean remoteHasHead = externalRepository.getRepository().resolve("HEAD") != null;
			final boolean localModified;
			writeLock.lock();
			try {
				localModified = remoteHasHead ? pull(localRepository, externalDir.toURI().toString(), remoteName) : false;
			} finally {
				writeLock.unlock();
			}
			final String remoteUri = externalDir.toURI().toString();
			if (bare) {
				final Iterable<PushResult> pushResults = localRepository.push().setRemote(remoteUri).setRefSpecs(new RefSpec("master:master")).call();
				boolean pushOk = true;
				for (final PushResult pushResult : pushResults) {
					for (final RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
						final Status status = update.getStatus();
						pushOk &= (status == Status.OK || status == Status.UP_TO_DATE);
					}
				}
				if (!pushOk) {
					final String conflictBranchName = findNextFreeConflictBranch(externalRepository, localName, new InfiniteCountIterator());
					final Iterable<PushResult> pushToFreeBranchResult = localRepository.push()
																																							.setRemote(remoteUri)
																																							.setRefSpecs(new RefSpec("refs/heads/master:refs/heads/conflict/" + localName
																																																				+ "/"
																																																				+ conflictBranchName))
																																							.call();
					for (final PushResult pushResult : pushToFreeBranchResult) {
						for (final RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
							final Status status = update.getStatus();
							if (status != Status.OK && status != Status.UP_TO_DATE) {
								stateManager.appendIssue(IssueType.UNKNOWN, null, null, "Cannot Push to " + remoteName + ": " + update.getMessage(), null);
							}
						}
					}
				} else {
					final ObjectId localHeadId = localRepository.getRepository().getRef(MASTER_REF).getObjectId();
					final ObjectId remoteHeadId = externalRepository.getRepository().getRef(MASTER_REF).getObjectId();
					if (!localHeadId.equals(remoteHeadId)) {
						log.error("Push error at " + externalDir);
					}
				}
			} else {
				final String localUri = localRepository.getRepository().getDirectory().toURI().toString();
				pull(externalRepository, localUri, localName);
			}

			return localModified;
		} catch (final Throwable e) {
			throw new RuntimeException("Cannot sync " + localRepository.getRepository() + " to " + externalDir, e);
		}
	}

	private Collection<Entry<String, Ref>> collectConflictBranches(final Git git) {
		final Collection<Entry<String, Ref>> foundConflicts = new ArrayList<Entry<String, Ref>>();
		for (final Entry<String, Ref> refEntry : git.getRepository().getAllRefs().entrySet()) {
			if (refEntry.getKey().startsWith(CONFLICT_BRANCH_PREFIX)) {
				foundConflicts.add(refEntry);
			}
		}
		return foundConflicts;
	}

	private String findNextFreeConflictBranch(final Git localRepo, final String serverName, final Iterator<String> iterator) {
		final Collection<String> existingConfictBranches = new HashSet<String>();
		for (final Entry<String, Ref> entry : collectConflictBranches(localRepo)) {
			existingConfictBranches.add(entry.getKey().substring(CONFLICT_BRANCH_PREFIX.length()));
		}
		while (iterator.hasNext()) {
			final String nextCandidate = serverName.replace(' ', '_') + "/" + iterator.next();
			if (!existingConfictBranches.contains(nextCandidate)) {
				return nextCandidate;
			}
		}
		return null;
	}

	private FileConflictCache loadConflictCache(final File conflictFile) {
		if (!conflictFile.exists()) {
			return new FileConflictCache();
		}
		try {
			return mapper.reader(FileConflictCache.class).<FileConflictCache> readValue(conflictFile);
		} catch (final IOException e) {
			log.error("Cannot load cache " + conflictFile, e);
			return new FileConflictCache();
		}
	}

	private Runnable makeIgnoreOtherRunnable(final Git git, final String branchCommit) {
		return new Runnable() {
			@Override
			public void run() {
				try {
					final ObjectId resolvedBranchCommit = git.getRepository().resolve(branchCommit);
					final MergeCommand mergeCommand = git.merge();
					mergeCommand.setStrategy(MergeStrategy.OURS);
					mergeCommand.include(resolvedBranchCommit);
					final MergeResult mergeResult = mergeCommand.call();
					final MergeStatus mergeStatus = mergeResult.getMergeStatus();
					log.info("Merged " + git.getRepository().getDirectory() + " Status: " + mergeStatus);
				} catch (final GitAPIException | RevisionSyntaxException | IOException e) {
					log.error("Canot merge on " + git.getRepository().getDirectory(), e);
				}
			}
		};
	}

	private Runnable makeIgnoreThisRunnable(final Git git, final String branchCommit) {
		return new Runnable() {
			@Override
			public void run() {
				try {
					final ObjectId resolvedBranchCommit = git.getRepository().resolve(branchCommit);
					final MergeCommand mergeCommand = git.merge();
					mergeCommand.setStrategy(MergeStrategy.THEIRS);
					mergeCommand.include(resolvedBranchCommit);
					final MergeResult mergeResult = mergeCommand.call();
					final MergeStatus mergeStatus = mergeResult.getMergeStatus();
					log.info("Merged " + git.getRepository().getDirectory() + " Status: " + mergeStatus);
				} catch (final GitAPIException | RevisionSyntaxException | IOException e) {
					log.error("Canot merge on " + git.getRepository().getDirectory(), e);
				}
			}
		};
	}
}
