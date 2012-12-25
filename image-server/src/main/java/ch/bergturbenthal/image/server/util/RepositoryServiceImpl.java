package ch.bergturbenthal.image.server.util;

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

import lombok.Cleanup;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ch.bergturbenthal.image.server.state.CloseableProgressMonitor;
import ch.bergturbenthal.image.server.state.StateManager;

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

  @Autowired
  private StateManager stateManager;

  private static final String NOTES_CONFLICT_PREFIX = "refs/notes/conflicts";
  private static final String CONFLICT_BRANCH_PREFIX = "refs/heads/conflict/";
  private final Set<MergeStatus> modifiedMergeStates = new HashSet<MergeStatus>(Arrays.asList(MergeStatus.FAST_FORWARD,
                                                                                              MergeStatus.FAST_FORWARD_SQUASHED, MergeStatus.MERGED,
                                                                                              MergeStatus.MERGED_SQUASHED));
  private static Logger logger = LoggerFactory.getLogger(RepositoryServiceImpl.class);

  private final ObjectMapper mapper = new ObjectMapper();

  /*
   * (non-Javadoc)
   * 
   * @see
   * ch.bergturbenthal.image.server.util.RepositoryService#cleanOldConflicts
   * (org.eclipse.jgit.api.Git)
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
    final Collection<ConflictEntry> conflicts = describeConflicts(git);
    if (conflicts.isEmpty())
      return;
    logger.info("Conflicts for " + git.getRepository());
    for (final ConflictEntry conflictEntry : conflicts) {
      logger.info("  - " + conflictEntry);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * ch.bergturbenthal.image.server.util.RepositoryService#describeConflicts
   * (org.eclipse.jgit.api.Git)
   */
  @Override
  public Collection<ConflictEntry> describeConflicts(final Git git) {
    final Map<AnyObjectId, ObjectId> notes = new HashMap<AnyObjectId, ObjectId>();
    try {
      final List<Note> foundNotes = git.notesList().setNotesRef(NOTES_CONFLICT_PREFIX).call();
      for (final Note note : foundNotes) {
        notes.put(note, note.getData());

      }
    } catch (final GitAPIException e) {
      logger.error("Cannot read notes from " + git.getRepository(), e);
    }
    final ArrayList<ConflictEntry> ret = new ArrayList<ConflictEntry>();
    for (final Entry<String, Ref> entry : collectConflictBranches(git)) {
      try {
        final ObjectReader objectReader = git.getRepository().newObjectReader();

        final CanonicalTreeParser branchTree;
        try {
          final RevWalk revWalk = new RevWalk(objectReader);
          final RevCommit commit = revWalk.parseCommit(entry.getValue().getObjectId());
          branchTree = new CanonicalTreeParser(null, objectReader, commit.getTree().getId());
        } finally {
          objectReader.release();
        }
        final List<DiffEntry> diffs = git.diff().setOldTree(branchTree).call();
        final ObjectId attachedNote = notes.get(entry.getValue().getObjectId());
        final ConflictMeta conflictMeta;
        if (attachedNote != null) {

          final ObjectLoader noteObjectLoader = git.getRepository().getObjectDatabase().open(attachedNote);
          final byte[] note = IOUtils.toByteArray(noteObjectLoader.openStream());
          conflictMeta = mapper.readValue(note, ConflictMeta.class);
        } else
          conflictMeta = null;
        final ConflictEntry conflictEntry = new ConflictEntry();
        conflictEntry.setBranch(entry.getKey());
        conflictEntry.setDiffs(diffs);
        conflictEntry.setMeta(conflictMeta);
        ret.add(conflictEntry);
      } catch (final GitAPIException e) {
        logger.error("Cannot parse branch " + entry.getKey(), e);
      } catch (final IncorrectObjectTypeException e) {
        logger.error("Cannot parse branch " + entry.getKey(), e);
      } catch (final IOException e) {
        logger.error("Cannot parse branch " + entry.getKey(), e);
      }

    }
    return ret;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * ch.bergturbenthal.image.server.util.RepositoryService#pull(org.eclipse.
   * jgit.api.Git, java.lang.String, java.lang.String)
   */
  @Override
  public boolean pull(final Git localRepo, final String remoteUri, final String serverName) {
    try {
      @Cleanup
      final CloseableProgressMonitor monitor = stateManager.makeProgressMonitor();
      localRepo.fetch().setRemote(remoteUri).setRefSpecs(new RefSpec("HEAD")).setProgressMonitor(monitor).call();
      final Repository repository = localRepo.getRepository();
      final Ref fetchHead = repository.getRef("FETCH_HEAD");
      final Ref headBefore = repository.getRef("HEAD");
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
          final Ref newBranch =
                                localRepo.branchCreate().setStartPoint(fetchHead.getObjectId().getName()).setName("conflict/" + nextConflictId)
                                         .call();
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
      final String nextCandidate = serverName + "/" + iterator.next();
      if (!existingConfictBranches.contains(nextCandidate))
        return nextCandidate;
    }
    return null;
  }
}
