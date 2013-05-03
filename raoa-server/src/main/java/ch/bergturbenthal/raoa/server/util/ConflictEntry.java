package ch.bergturbenthal.raoa.server.util;

import java.util.Collection;

import lombok.Data;

import org.eclipse.jgit.diff.DiffEntry;

@Data
public class ConflictEntry {
	private String branch;
	private Collection<DiffEntry> diffs;
	private ConflictMeta meta;
}
