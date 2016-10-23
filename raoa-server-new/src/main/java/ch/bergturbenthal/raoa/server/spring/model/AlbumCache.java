package ch.bergturbenthal.raoa.server.spring.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import lombok.Value;

@Value
public class AlbumCache {
	private AlbumMetadata albumMetadata;
	private List<Instant> autoAddBeginDates;
	private Map<String, AlbumEntryData> entries;
	private Map<String, ObjectId> files;
	private ObjectId lastCommit;
}
