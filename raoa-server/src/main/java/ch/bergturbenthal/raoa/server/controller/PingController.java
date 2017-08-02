package ch.bergturbenthal.raoa.server.controller;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.jgit.transport.Daemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.ArchiveConfiguration;

@Controller
@RequestMapping("/rest/ping")
public class PingController {
	@Autowired
	private ArchiveConfiguration archiveConfiguration;
	@Autowired
	private AlbumAccess dataAccess;
	@Autowired
	private Daemon gitDaemon;

	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody PingResponse ping() {
		final PingResponse response = new PingResponse();
		response.setServerId(dataAccess.getInstanceId());
		response.setServerName(archiveConfiguration.getInstanceName());
		response.setArchiveId(archiveConfiguration.getArchiveName());
		response.setGitPort(gitDaemon.getAddress().getPort());
		return response;
	}

	@PostConstruct
	public void startDaemon() throws IOException {
		gitDaemon.start();
	}

	@PreDestroy
	public void stopDaemon() {
		gitDaemon.stop();
	}
}
