package ch.bergturbenthal.image.server.controller;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.jgit.transport.Daemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.server.AlbumAccess;
import ch.bergturbenthal.image.server.ArchiveConfiguration;

@Controller
@RequestMapping("/ping")
public class PingController {
  @Autowired
  private AlbumAccess dataAccess;
  @Autowired
  private ArchiveConfiguration archiveConfiguration;
  @Autowired
  private Daemon gitDaemon;

  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  PingResponse ping() {
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
