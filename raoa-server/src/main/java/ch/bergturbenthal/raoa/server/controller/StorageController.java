package ch.bergturbenthal.raoa.server.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.bergturbenthal.raoa.data.model.StorageList;
import ch.bergturbenthal.raoa.server.StorageAccess;

@Controller
@RequestMapping("/storages")
public class StorageController {
  private static Logger logger = LoggerFactory.getLogger(AlbumController.class);
  @Autowired
  private StorageAccess storageAccess;

  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody
  StorageList listClients() {
    return storageAccess.listKnownStorage();
  }
}
