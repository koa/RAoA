package ch.bergturbenthal.image.server.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.image.data.model.PingResponse;
import ch.bergturbenthal.image.server.AlbumAccess;

@Controller
@RequestMapping("/ping")
public class PingController {
  private final String instanceId = UUID.randomUUID().toString();
  @Autowired
  private AlbumAccess dataAccess;

  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  PingResponse ping() {
    final PingResponse response = new PingResponse();
    response.setServerId(instanceId);
    response.setCollectionId(dataAccess.getCollectionId());
    return response;
  }
}
