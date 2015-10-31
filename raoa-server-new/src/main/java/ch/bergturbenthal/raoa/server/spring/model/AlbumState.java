package ch.bergturbenthal.raoa.server.spring.model;

import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import lombok.Value;

@Value
public class AlbumState {
	private AlbumMetadata albumMetadata;
	private long createTime = System.currentTimeMillis();
	private Map<String, AlbumEntryData> entries;
	private Map<String, ObjectId> files;
	private ObjectId lastCommit;
}
