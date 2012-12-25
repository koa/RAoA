/*
 * (c) 2012 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.server.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import lombok.Data;

/**
 * Description of a archive
 * 
 */
@Data
public class ArchiveData {
  private String archiveName;
  private final Map<String, Collection<String>> albumPerStorage = new TreeMap<String, Collection<String>>();
}
