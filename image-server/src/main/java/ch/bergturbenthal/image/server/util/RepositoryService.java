package ch.bergturbenthal.image.server.util;

import java.util.Collection;

import org.eclipse.jgit.api.Git;

public interface RepositoryService {

  /**
   * checks all conflict-branches and removes all obsolete
   * 
   * @param git
   *          repository to cleanup
   */
  void cleanOldConflicts(final Git git);

  Collection<ConflictEntry> describeConflicts(final Git git);

  /**
   * implements pull with a simple conflict-handling
   * 
   * @param localRepo
   *          locale repository to pull into
   * @param remoteUri
   *          remote repository to take master from
   * @param serverName
   *          name of the polling server
   * @return true master was modified
   */
  boolean pull(final Git localRepo, final String remoteUri, final String serverName);

}