package ch.bergturbenthal.raoa.server.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class FileConflictCache {
	private Map<String, BranchConflictEntry> conflicts = new HashMap<>();
}
