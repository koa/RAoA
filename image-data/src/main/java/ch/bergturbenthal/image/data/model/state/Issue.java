package ch.bergturbenthal.image.data.model.state;

import java.util.Date;

import lombok.Data;

@Data
public class Issue {
  private String issueId;
  private Date issueTime;
  private IssueType type;
  private String stackTrace;
  private String imageName;
  private String albumName;
  private boolean acknowledgable;
}
