package ch.bergturbenthal.raoa.server.controller;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.data.model.state.IssueResolveAction;
import ch.bergturbenthal.raoa.data.model.state.ServerState;
import ch.bergturbenthal.raoa.server.state.StateManager;

@Controller
@RequestMapping("/rest/state")
public class StateController {
	@Autowired
	private StateManager stateManager;

	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody ServerState getServerState() {
		return stateManager.getCurrentState();
	}

	@RequestMapping(method = RequestMethod.PUT, value = "issue/{issueId}/resolve")
	public void resolveIssue(	@PathVariable("issueId") final String issueId,
														@RequestBody final IssueResolveAction action,
														final HttpServletResponse response) throws UnsupportedEncodingException {
		stateManager.resolveIssue(URLDecoder.decode(issueId, "utf-8"), action);
	}
}
