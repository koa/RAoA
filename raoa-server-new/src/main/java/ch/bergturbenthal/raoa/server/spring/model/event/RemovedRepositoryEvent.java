package ch.bergturbenthal.raoa.server.spring.model.event;

import java.time.Instant;

import lombok.NonNull;
import lombok.Value;

@Value
public class RemovedRepositoryEvent implements Event {
	@NonNull
	private String repositoryId;
	@NonNull
	private Instant time;
}
