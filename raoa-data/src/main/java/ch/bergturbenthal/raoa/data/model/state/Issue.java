package ch.bergturbenthal.raoa.data.model.state;

import java.util.Date;
import java.util.Set;

import lombok.Data;

@Data
public class Issue {
	private String albumName;
	private Set<IssueResolveAction> availableActions;
	private String imageName;
	private String issueId;
	private Date issueTime;
	private String stackTrace;
	private IssueType type;
}
