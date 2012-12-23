package ch.bergturbenthal.image.server.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.joda.time.format.ISODateTimeFormat;

public class RepositoryUtil {
  private static Set<MergeStatus> modifiedMergeStates = new HashSet<MergeStatus>(Arrays.asList(MergeStatus.FAST_FORWARD,
                                                                                               MergeStatus.FAST_FORWARD_SQUASHED, MergeStatus.MERGED,
                                                                                               MergeStatus.MERGED_SQUASHED));

  /**
   * implements pull with a simple conflict-handling
   * 
   * @param localRepo
   *          locale repository to pull into
   * @param remoteUri
   *          remote repository to take master from
   * @return true master was modified
   */
  public static boolean pull(final Git localRepo, final String remoteUri) {
    try {
      localRepo.fetch().setRemote(remoteUri).setRefSpecs(new RefSpec("HEAD")).call();
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
        if (!alreadyBranch)
          // make a conflict branch with taken version
          localRepo.branchCreate().setStartPoint(fetchHead.getObjectId().getName())
                   .setName("conflict/" + ISODateTimeFormat.basicDateTime().print(System.currentTimeMillis())).call();
      }
      return modifiedMergeStates.contains(mergeStatus);
    } catch (final GitAPIException e) {
      throw new RuntimeException("Cannot sync local repo " + localRepo.getRepository().toString() + " with " + remoteUri, e);
    } catch (final IOException e) {
      throw new RuntimeException("Cannot sync local repo " + localRepo.getRepository().toString() + " with " + remoteUri, e);
    }
  }
}
