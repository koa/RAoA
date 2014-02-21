package ch.bergturbenthal.raoa.server.util;

import java.util.Collection;
import java.util.Map;

import lombok.Data;

import org.eclipse.jgit.diff.DiffEntry;

import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;

@Data
public class ConflictEntry {
	private String branch;
	private Collection<DiffEntry> diffs;
	private ConflictMeta meta;
	private Map<IssueResolveAction, Runnable> resolveActions;
}
