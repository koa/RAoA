package ch.bergturbenthal.raoa.server.spring.model;

import org.eclipse.jgit.lib.ObjectId;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BranchState {
	private ObjectId lastCommit;
	private long lastRefreshTime;
}
