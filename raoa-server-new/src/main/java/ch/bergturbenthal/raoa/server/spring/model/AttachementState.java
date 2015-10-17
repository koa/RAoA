package ch.bergturbenthal.raoa.server.spring.model;

import java.util.Map;

import lombok.Value;

import org.eclipse.jgit.lib.ObjectId;

@Value
public class AttachementState {
	private String commitId;
	private long createTime = System.currentTimeMillis();
	private Map<String, ObjectId> existingAttachements;
	private ObjectId lastAttachementCommit;
}
