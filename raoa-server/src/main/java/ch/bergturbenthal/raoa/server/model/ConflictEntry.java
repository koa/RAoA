package ch.bergturbenthal.raoa.server.model;

import java.util.Collection;
import java.util.Map;

import lombok.Data;
import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.server.util.ConflictMeta;

@Data
public class ConflictEntry {
	private String branch;
	private Collection<FileConflictEntry> diffs;
	private ConflictMeta meta;
	private Map<IssueResolveAction, Runnable> resolveActions;
}
