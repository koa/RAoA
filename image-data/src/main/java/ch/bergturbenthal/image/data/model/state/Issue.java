package ch.bergturbenthal.image.data.model.state;

import java.util.Date;

import lombok.Data;

@Data
public abstract class Issue {
  private Date issueTime;
  private String issueId;

  public abstract boolean canAcknownledge();
}
