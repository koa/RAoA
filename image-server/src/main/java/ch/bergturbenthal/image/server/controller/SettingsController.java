package ch.bergturbenthal.image.server.controller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import ch.bergturbenthal.image.server.ArchiveConfiguration;
import ch.bergturbenthal.image.server.FileConfiguration;
import ch.bergturbenthal.image.server.model.SettingsData;

@Controller
@RequestMapping("/settings")
public class SettingsController {
  private static Logger logger = LoggerFactory.getLogger(SettingsController.class);
  @Autowired
  private FileConfiguration fileConfiguration;
  @Autowired
  private ArchiveConfiguration archiveConfiguration;

  @ModelAttribute("settingsData")
  public SettingsData makeSettingsData() {
    final SettingsData ret = new SettingsData();
    ret.setAlbumPath(fileConfiguration.getBaseDir());
    ret.setImportBasePath(fileConfiguration.getImportBaseDir());
    ret.setInstanceName(archiveConfiguration.getInstanceName());
    ret.setArchiveName(archiveConfiguration.getArchiveName());
    return ret;
  }

  @RequestMapping(method = RequestMethod.GET)
  public String showCurrentSettingsPage() {
    return "settings";
  }

  @RequestMapping(method = RequestMethod.POST)
  public String updateSettings(@Validated @ModelAttribute("settingsData") final SettingsData data, final BindingResult result) {
    final File importBasePath = data.getImportBasePath();
    if (importBasePath == null || !importBasePath.exists())
      result.addError(new FieldError("settingsData", "importBasePath", importBasePath, false, null, null, "Directory does not exist"));
    final File albumPath = data.getAlbumPath();
    if (!albumPath.exists()) {
      final boolean created = albumPath.mkdirs();
      if (!created)
        result.addError(new FieldError("settingsData", "albumPath", albumPath, false, null, null, "Directory cannot be created"));
    }
    if (albumPath.exists()) {
      if (!albumPath.isDirectory())
        result.addError(new FieldError("settingsData", "albumPath", albumPath, false, null, null, "Is not a directory"));
      else if (!albumPath.canWrite())
        result.addError(new FieldError("settingsData", "albumPath", albumPath, false, null, null, "cannot write to this directory"));
    }
    if (result.hasErrors())
      // display error messages
      return "settings";
    fileConfiguration.setBaseDir(albumPath);
    fileConfiguration.setImportBaseDir(importBasePath);
    archiveConfiguration.setArchiveName(data.getArchiveName());
    archiveConfiguration.setInstanceName(data.getInstanceName());
    return "settings";
  }
}
