package ch.bergturbenthal.image.data.model.state;

import lombok.Data;

@Data
public class SyncConflict extends Issue {
  private String peerServerName;
  private String albumId;

  @Override
  public boolean canAcknownledge() {
    return false;
  }
}
