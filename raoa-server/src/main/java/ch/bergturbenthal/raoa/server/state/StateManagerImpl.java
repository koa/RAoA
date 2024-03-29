package ch.bergturbenthal.raoa.server.state;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;

import ch.bergturbenthal.raoa.data.model.state.Issue;
import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.IssueType;
import ch.bergturbenthal.raoa.data.model.state.Progress;
import ch.bergturbenthal.raoa.data.model.state.ProgressType;
import ch.bergturbenthal.raoa.data.model.state.ServerState;
import ch.bergturbenthal.raoa.server.model.ConflictEntry;
import ch.bergturbenthal.raoa.server.model.FileConflictEntry;
import ch.bergturbenthal.raoa.server.util.ConflictMeta;

public class StateManagerImpl implements StateManager {
	private class JGitProgressMonitor extends BatchingProgressMonitor implements CloseableProgressMonitor {
		private final String progressId = UUID.randomUUID().toString();

		@Override
		public void close() throws IOException {
			runningProgress.remove(progressId);
			pushUpdates();
		}

		@Override
		protected void onEndTask(final String taskName, final int workCurr) {
			runningProgress.remove(progressId);
			pushUpdates();
		}

		@Override
		protected void onEndTask(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
			runningProgress.remove(progressId);
			pushUpdates();
		}

		@Override
		protected void onUpdate(final String taskName, final int workCurr) {
			final Progress progress = new Progress();
			progress.setProgressId(progressId);
			progress.setProgressDescription(taskName);
			progress.setType(ProgressType.GIT);
			runningProgress.put(progressId, progress);
			pushUpdates();
		}

		@Override
		protected void onUpdate(final String taskName, final int workCurr, final int workTotal, final int percentDone) {
			final Progress progress = new Progress();
			progress.setProgressId(progressId);
			progress.setProgressDescription(taskName);
			progress.setType(ProgressType.GIT);
			progress.setStepCount(workTotal);
			progress.setCurrentStepNr(workCurr);
			runningProgress.put(progressId, progress);
			pushUpdates();
		}
	}

	private final class ProgressHandlerImplementation implements ProgressHandler {
		private boolean closed = false;
		private final Map<Integer, String> currentRunningSteps = Collections.synchronizedMap(new LinkedHashMap<Integer, String>());
		private final AtomicInteger doneCounter = new AtomicInteger(0);
		private final AtomicInteger nextStepId = new AtomicInteger(0);
		private final String progressDescription;
		private final String progressId;
		private final AtomicInteger startedCounter = new AtomicInteger(0);
		private final int totalCount;
		private final ProgressType type;

		private ProgressHandlerImplementation(final String progressId, final ProgressType type, final int totalCount, final String progressDescription) {
			this.progressId = progressId;
			this.type = type;
			this.totalCount = totalCount;
			this.progressDescription = progressDescription;
		}

		@Override
		public void close() {
			closed = true;
			runningProgress.remove(progressId);
			pushUpdates();
		}

		private String decodeRunningSteps(final Map<Integer, String> currentRunningSteps) {
			synchronized (currentRunningSteps) {
				return StringUtils.join(currentRunningSteps.values(), ',');
			}
		}

		@Override
		public void finishProgress() {
			if (doneCounter.incrementAndGet() >= totalCount) {
				close();
			}
		}

		@Override
		public Closeable notfiyProgress(final String description) {
			final Integer stepKey = Integer.valueOf(nextStepId.getAndIncrement());
			currentRunningSteps.put(stepKey, description);
			updateState(startedCounter.incrementAndGet(), doneCounter.get(), decodeRunningSteps(currentRunningSteps));
			return new Closeable() {

				@Override
				public void close() {
					currentRunningSteps.remove(stepKey);
					updateState(startedCounter.get(), doneCounter.incrementAndGet(), decodeRunningSteps(currentRunningSteps));

				}
			};
		}

		private void updateState(final int startedCounter, final int doneCounter, final String description) {
			if (closed) {
				return;
			}
			final Progress progress = new Progress();
			progress.setProgressId(progressId);
			progress.setStepCount(totalCount * 2);
			progress.setCurrentStepNr(startedCounter + doneCounter);
			progress.setProgressDescription(progressDescription);
			progress.setCurrentStepDescription(description);
			progress.setType(type);
			runningProgress.put(progressId, progress);
			pushUpdates();
		}
	}

	@Data
	private class TroubleOrigin {
		private final IssueType issueType;
	}

	private final ConcurrentMap<String, Issue> acknowledgableIssues = new ConcurrentHashMap<>();

	private final ConcurrentMap<String, Map<IssueResolveAction, Runnable>> conflictTroubleResolver = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Issue> conflictTroubles = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Collection<Issue>> exceptionTroublesPerAlbum = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Collection<Issue>> exceptionTroublesPerThumbnail = new ConcurrentHashMap<>();
	@Autowired
	private ExecutorService executor;
	private final ConcurrentMap<String, Progress> runningProgress = new ConcurrentHashMap<String, Progress>();

	@Override
	public void appendIssue(final IssueType type, final String album, final String image, final String message, final Throwable exception) {
		final String id = UUID.randomUUID().toString();
		final Issue issue = new Issue();
		issue.setAvailableActions(Collections.singleton(IssueResolveAction.ACKNOWLEDGE));
		issue.setIssueId(id);
		issue.setAlbumName(album);
		issue.setDetailName(image);
		issue.setIssueTime(new Date());
		issue.setType(type);
		final StringBuffer messageBuffer = new StringBuffer();
		if (message != null) {
			messageBuffer.append(message);
		}
		if (exception != null) {
			messageBuffer.append(takeStacktrace(exception));
		}
		issue.setDetails(messageBuffer.toString());
		acknowledgableIssues.put(id, issue);
	}

	@Override
	public void clearException(final String relativePath) {
		exceptionTroublesPerAlbum.remove(relativePath);
	}

	@Override
	public void clearThumbnailException(final String name, final String image) {
		exceptionTroublesPerThumbnail.remove(name + "/" + image);
	}

	private void decodeException(final Throwable ex, final ArrayList<TroubleOrigin> troubles) {
		if (ex instanceof BeanCreationException) {
			final BeanCreationException factoryException = (BeanCreationException) ex;
			final Throwable[] relatedCauses = factoryException.getRelatedCauses();
			if (relatedCauses != null && relatedCauses.length > 0) {
				for (final Throwable throwable : relatedCauses) {
					decodeException(throwable, troubles);
				}
				return;
			}
		}
		if (ex instanceof JGitInternalException) {
			final JGitInternalException gitException = (JGitInternalException) ex;
			final Throwable cause = gitException.getCause();
			if (cause != null && cause != gitException) {
				if (cause instanceof LockFailedException) {
					troubles.add(new TroubleOrigin(IssueType.ALBUM_LOCKED));
					return;
				}
			}
		}
		troubles.add(new TroubleOrigin(IssueType.UNKNOWN));
	}

	private String describeConflicts(final ConflictEntry conflictEntry) {
		final StringBuilder added = new StringBuilder();
		final StringBuilder copied = new StringBuilder();
		final StringBuilder deleted = new StringBuilder();
		final StringBuilder renamed = new StringBuilder();
		final StringBuilder modified = new StringBuilder();
		if (conflictEntry.getDiffs() != null) {
			for (final FileConflictEntry diff : conflictEntry.getDiffs()) {
				switch (diff.getChangeType()) {
				case ADD:
					added.append(diff.getNewPath());
					added.append("\n");
					break;
				case COPY:
					copied.append(diff.getOldPath());
					copied.append(" -> ");
					copied.append(diff.getNewPath());
					copied.append("\n");
					break;
				case DELETE:
					deleted.append(diff.getOldPath());
					deleted.append("\n");
					break;
				case MODIFY:
					modified.append(diff.getNewPath());
					modified.append("\n");
					break;
				case RENAME:
					renamed.append(diff.getOldPath());
					renamed.append(" -> ");
					renamed.append(diff.getNewPath());
					renamed.append("\n");
					break;
				}
			}
		}
		final StringBuilder description = new StringBuilder();
		if (added.length() > 0) {
			description.append("Added:\n\n");
			description.append(added);
			description.append("\n");
		}
		if (deleted.length() > 0) {
			description.append("Deleted:\n\n");
			description.append(deleted);
			description.append("\n");
		}
		if (modified.length() > 0) {
			description.append("Modified:\n\n");
			description.append(modified);
			description.append("\n");
		}
		if (copied.length() > 0) {
			description.append("Copied:\n\n");
			description.append(copied);
			description.append("\n");
		}
		if (renamed.length() > 0) {
			description.append("Renamed:\n\n");
			description.append(renamed);
			description.append("\n");
		}
		return description.toString();

	}

	@Override
	public ServerState getCurrentState() {
		final ServerState serverState = new ServerState();
		serverState.setProgress(runningProgress.values());
		final Collection<Issue> issues = new ArrayList<>();
		for (final Collection<Issue> exceptionIssues : exceptionTroublesPerAlbum.values()) {
			issues.addAll(exceptionIssues);
		}
		for (final Collection<Issue> exceptionIssues : exceptionTroublesPerThumbnail.values()) {
			issues.addAll(exceptionIssues);
		}
		issues.addAll(conflictTroubles.values());
		issues.addAll(acknowledgableIssues.values());
		serverState.setIssues(issues);
		return serverState;
	}

	@Override
	public CloseableProgressMonitor makeProgressMonitor() {

		return new JGitProgressMonitor();
	}

	@Override
	public ProgressHandler newProgress(final int totalCount, final ProgressType type, final String progressDescription) {
		final String progressId = UUID.randomUUID().toString();
		return new ProgressHandlerImplementation(progressId, type, totalCount, progressDescription);
	}

	private void pushUpdates() {
		// System.out.println("------------------------------------------");
		// for (final Progress progress : runningProgress.values()) {
		// System.out.println(progress);
		// }
		// System.out.println("------------------------------------------");
	}

	@Override
	public void recordException(final String relativePath, final Throwable ex) {
		final ArrayList<TroubleOrigin> troubles = new ArrayList<>();
		decodeException(ex, troubles);
		final String stackTrace = takeStacktrace(ex);
		exceptionTroublesPerAlbum.putIfAbsent(relativePath, new ArrayList<Issue>());
		final Collection<Issue> issues = exceptionTroublesPerAlbum.get(relativePath);
		for (final TroubleOrigin origin : troubles) {
			final Issue issue = new Issue();
			issue.setType(origin.getIssueType());
			issue.setAlbumName(relativePath);
			issue.setIssueTime(new Date());
			issue.setDetails(stackTrace);
			issue.setAvailableActions(Collections.<IssueResolveAction> emptySet());
			issue.setIssueId(UUID.randomUUID().toString());
			issues.add(issue);
		}
		exceptionTroublesPerAlbum.put(relativePath, issues);
	}

	@Override
	public void recordThumbnailException(final String name, final String image, final Throwable ex) {
		final ArrayList<TroubleOrigin> troubles = new ArrayList<>();
		decodeException(ex, troubles);
		final String stackTrace = takeStacktrace(ex);
		final Collection<Issue> issues = new ArrayList<>();
		for (final TroubleOrigin origin : troubles) {
			final Issue issue = new Issue();
			issue.setType(origin.getIssueType());
			issue.setAlbumName(name);
			issue.setDetailName(image);
			issue.setIssueTime(new Date());
			issue.setDetails(stackTrace);
			issue.setAvailableActions(Collections.<IssueResolveAction> emptySet());
			issue.setIssueId(UUID.randomUUID().toString());
			issues.add(issue);
		}
		exceptionTroublesPerThumbnail.put(name + "/" + image, issues);
	}

	@Override
	public void reportConflict(final String albumName, final Collection<ConflictEntry> conflicts) {
		final String albumPrefix = albumName + "-";
		final Map<String, Issue> newConflictTroubles = new HashMap<String, Issue>();
		final Map<String, Map<IssueResolveAction, Runnable>> newConflictResolver = new HashMap<>();
		if (conflicts != null && !conflicts.isEmpty()) {
			for (final ConflictEntry conflictEntry : conflicts) {
				final ConflictMeta meta = conflictEntry.getMeta();
				final String conflictEntryKey = albumPrefix + conflictEntry.getBranch();
				final Issue issue = new Issue();
				final Map<IssueResolveAction, Runnable> resolveActions = conflictEntry.getResolveActions();
				if (resolveActions == null) {
					issue.setAvailableActions(Collections.<IssueResolveAction> emptySet());
				} else {
					issue.setAvailableActions(new HashSet<>(resolveActions.keySet()));
				}
				issue.setAlbumName(albumName);
				issue.setDetailName(conflictEntry.getBranch());
				issue.setIssueId(conflictEntryKey);
				issue.setType(IssueType.SYNC_CONFLICT);
				issue.setDetails(describeConflicts(conflictEntry));
				issue.setIssueTime(meta.getConflictDate());
				if (resolveActions != null) {
					newConflictResolver.put(conflictEntryKey, resolveActions);
				}
				newConflictTroubles.put(conflictEntryKey, issue);
			}
		}
		for (final Iterator<Entry<String, Map<IssueResolveAction, Runnable>>> iterator = conflictTroubleResolver.entrySet().iterator(); iterator.hasNext();) {
			final Entry<String, Map<IssueResolveAction, Runnable>> troubleResolverEntry = iterator.next();
			if (troubleResolverEntry.getKey().startsWith(albumPrefix)) {
				final Map<IssueResolveAction, Runnable> updatedIssue = newConflictResolver.remove(troubleResolverEntry.getKey());
				if (updatedIssue == null) {
					iterator.remove();
				} else {
					troubleResolverEntry.setValue(updatedIssue);
				}
			}
		}
		conflictTroubleResolver.putAll(newConflictResolver);
		for (final Iterator<Entry<String, Issue>> iterator = conflictTroubles.entrySet().iterator(); iterator.hasNext();) {
			final Entry<String, Issue> troubleEntry = iterator.next();
			if (troubleEntry.getKey().startsWith(albumPrefix)) {
				final Issue updatedIssue = newConflictTroubles.remove(troubleEntry.getKey());
				if (updatedIssue == null) {
					iterator.remove();
				} else {
					troubleEntry.setValue(updatedIssue);
				}
			}
		}
		conflictTroubles.putAll(newConflictTroubles);
	}

	@Override
	public void resolveIssue(final String issueId, final IssueResolveAction action) {
		switch (action) {
		case ACKNOWLEDGE:
			acknowledgableIssues.remove(issueId);
			break;
		case IGNORE_OTHER:
		case IGNORE_THIS:
			final Map<IssueResolveAction, Runnable> availableActions = conflictTroubleResolver.get(issueId);
			if (availableActions == null) {
				break;
			}
			final Runnable actionRunnable = availableActions.get(action);
			if (actionRunnable == null) {
				break;
			}
			// remove trouble -> it will coming back if there is not solved with this change
			conflictTroubles.remove(issueId);
			executor.submit(actionRunnable);
			break;
		}
		// TODO Auto-generated method stub

	}

	private String takeStacktrace(final Throwable ex) {
		final StringWriter stackTraceWriter = new StringWriter();
		ex.printStackTrace(new PrintWriter(stackTraceWriter));
		final String stackTrace = stackTraceWriter.toString();
		return stackTrace;
	}

}
