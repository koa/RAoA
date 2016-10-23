package ch.bergturbenthal.raoa.server.spring.controller;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ch.bergturbenthal.raoa.data.model.PingResponse;
import ch.bergturbenthal.raoa.json.InstanceData;
import ch.bergturbenthal.raoa.server.spring.service.AlbumAccess;

@RestController
@RequestMapping("/ping")
public class PingController {
	@Autowired
	private AlbumAccess dataAccess;

	// @Autowired
	// private Daemon gitDaemon;

	@RequestMapping(method = RequestMethod.GET)
	public PingResponse ping() {
		final InstanceData instanceData = dataAccess.getInstanceData();
		final PingResponse response = new PingResponse();
		response.setServerId(instanceData.getInstanceId());
		response.setServerName(instanceData.getInstanceName());
		// response.setGitPort(gitDaemon.getAddress().getPort());
		return response;
	}

	@PostConstruct
	public void startDaemon() throws IOException {
		// gitDaemon.start();
	}

	@PreDestroy
	public void stopDaemon() {
		// gitDaemon.stop();
	}
}
