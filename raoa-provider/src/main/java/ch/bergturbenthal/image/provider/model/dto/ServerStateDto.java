package ch.bergturbenthal.image.provider.model.dto;

import ch.bergturbenthal.image.data.model.state.ServerState;

public class ServerStateDto {
  private final ServerState serverState;
  private final String serverName;

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
