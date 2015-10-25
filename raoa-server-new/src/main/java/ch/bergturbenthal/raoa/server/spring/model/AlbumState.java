package ch.bergturbenthal.raoa.server.spring.model;

import java.util.Map;

import lombok.Value;

import org.eclipse.jgit.lib.ObjectId;

@Value
public class AlbumState {
	private long createTime = System.currentTimeMillis();
	private Map<String, AlbumEntryData> entries;
	private Map<String, ObjectId> files;
	private ObjectId lastCommit;
}
