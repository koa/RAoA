package ch.bergturbenthal.raoa.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import org.eclipse.jgit.diff.DiffEntry;

@Data
@NoArgsConstructor
public class FileConflictEntry {
	private DiffEntry.ChangeType changeType;
	private String newPath;
	private String oldPath;

	public FileConflictEntry(final DiffEntry originalEntry) {
		changeType = originalEntry.getChangeType();
		newPath = originalEntry.getNewPath();
		oldPath = originalEntry.getOldPath();
	}
}
