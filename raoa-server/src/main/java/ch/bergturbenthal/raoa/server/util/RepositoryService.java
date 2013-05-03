package ch.bergturbenthal.raoa.server.util;

import java.io.File;
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

	/**
	 * Sync to a external File
	 * 
	 * @param localRepository
	 *          local Repository
	 * @param externalDir
	 * @param localName
	 *          name of the local server
	 * @param remoteName
	 *          name of the remote disc
	 * @param boolean bare;
	 * @return true means the local repository is modified
	 */
	boolean sync(final Git localRepository, final File externalDir, final String localName, final String remoteName, final boolean bare);

	/**
	 * check if a given directory is a repository
	 * 
	 * @param directory
	 *          directory to check
	 * @param bare
	 *          should be a bare repository
	 * @return true repository found
	 */
	boolean isRepository(final File directory, final boolean bare);
}