package ch.bergturbenthal.raoa.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.data.model.state.ServerState;
import ch.bergturbenthal.raoa.server.state.StateManager;

@Controller
@RequestMapping("/state")
public class StateController {
  @Autowired
  private StateManager stateManager;

  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  ServerState getServerState() {
    return stateManager.getCurrentState();
  }
}
