package ch.bergturbenthal.raoa.provider.model.dto;

import ch.bergturbenthal.raoa.data.model.state.ServerState;

public class ServerStateDto {
	private final String serverName;
	private final ServerState serverState;

	public ServerStateDto(final String serverName, final ServerState serverState) {
		this.serverName = serverName;
		this.serverState = serverState;
	}

	public String getServerName() {
		return serverName;
	}

	public ServerState getServerState() {
		return serverState;
	}

}
