package ch.bergturbenthal.raoa.provider.model.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Builder;
import ch.bergturbenthal.raoa.data.model.state.ServerState;

@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ServerStateDto {
	private final String	    serverName;
	private final ServerState	serverState;
}
