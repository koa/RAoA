package ch.bergturbenthal.raoa.server.spring.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Builder;

@Data
@Builder
public class AlbumEntryData {
	private final Map<Class<? extends Object>, Set<ObjectId>> attachedFiles = new HashMap<>();
	@NonNull
	private final String filename;
	private final Map<String, ObjectId> generatedAttachements = new HashMap<>();
	@NonNull
	private final ObjectId originalFileId;
}
