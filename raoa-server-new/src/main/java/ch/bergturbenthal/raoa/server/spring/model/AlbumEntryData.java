package ch.bergturbenthal.raoa.server.spring.model;

import lombok.Data;
import lombok.experimental.Builder;

import org.eclipse.jgit.lib.ObjectId;

@Data
@Builder
public class AlbumEntryData {
	private final ObjectId originalFileId;
	private ObjectId thumbailId;
}
