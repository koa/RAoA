package ch.bergturbenthal.raoa.server.spring.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Builder;

import org.eclipse.jgit.lib.ObjectId;

@Data
@Builder
public class AlbumEntryData {
	private final Map<String, ObjectId> attachements = new HashMap<String, ObjectId>();
	private ObjectId metadataSidecarId;
	private final ObjectId originalFileId;
}
