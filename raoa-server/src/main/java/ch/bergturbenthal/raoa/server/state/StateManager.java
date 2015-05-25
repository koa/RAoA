package ch.bergturbenthal.raoa.server.state;

import java.util.Collection;

import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.IssueType;
import ch.bergturbenthal.raoa.data.model.state.ProgressType;
import ch.bergturbenthal.raoa.data.model.state.ServerState;
import ch.bergturbenthal.raoa.server.model.ConflictEntry;

public interface StateManager {
	ProgressHandler newProgress(final int totalCount, final ProgressType type, final String progressDescription);

	CloseableProgressMonitor makeProgressMonitor();

	ServerState getCurrentState();

	void recordException(final String relativePath, final Throwable ex);

	void clearException(final String relativePath);

	void recordThumbnailException(final String name, final String image, final Throwable ex);

	void clearThumbnailException(final String name, final String image);

	void reportConflict(final String albumName, final Collection<ConflictEntry> conflicts);

	void appendIssue(final IssueType type, final String album, final String image, final String message, final Throwable exception);

	void resolveIssue(final String issueId, final IssueResolveAction action);
}
