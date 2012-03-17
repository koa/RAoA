package ch.bergturbenthal.image.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.image.data.model.PingResponse;

@Controller
@RequestMapping("/ping")
public class PingController {
  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  PingResponse ping() {
    return new PingResponse();
  }
}
