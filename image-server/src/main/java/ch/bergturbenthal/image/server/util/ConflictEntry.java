package ch.bergturbenthal.image.server.util;

import java.util.Collection;

import lombok.Data;

import org.eclipse.jgit.diff.DiffEntry;

@Data
public class ConflictEntry {
  private ConflictMeta meta;
  private Collection<DiffEntry> diffs;
  private String branch;
}
