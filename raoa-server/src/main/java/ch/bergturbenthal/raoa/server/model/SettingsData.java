package ch.bergturbenthal.raoa.server.model;

import java.io.File;

import lombok.Data;

/**
 * all editable settings collected for editor
 * 
 */
@Data
public class SettingsData {
	private File albumPath;
	private String archiveName;
	private File importBasePath;
	private String instanceName;
}
