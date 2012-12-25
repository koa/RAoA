package ch.bergturbenthal.image.server.model;

import java.io.File;

import lombok.Data;

/**
 * all editable settings collected for editor
 * 
 */
@Data
public class SettingsData {
  private File albumPath;
  private File importBasePath;
  private String instanceName;
  private String archiveName;
}
