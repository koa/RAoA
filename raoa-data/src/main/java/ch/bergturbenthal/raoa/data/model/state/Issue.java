package ch.bergturbenthal.raoa.data.model.state;

import java.util.Date;
import java.util.Set;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue {
	private String albumName;
	private Set<IssueResolveAction> availableActions;
	private String detailName;
	private String details;
	private String issueId;
	private Date issueTime;
	private IssueType type;
}
