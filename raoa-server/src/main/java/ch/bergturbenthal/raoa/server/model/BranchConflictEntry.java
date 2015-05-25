package ch.bergturbenthal.raoa.server.model;

import java.util.Collection;

import lombok.Data;
import ch.bergturbenthal.raoa.server.util.ConflictMeta;

@Data
public class BranchConflictEntry {
	private String branchCommit;
	private ConflictMeta conflictMeta;
	private Collection<FileConflictEntry> fileEntries;

}
