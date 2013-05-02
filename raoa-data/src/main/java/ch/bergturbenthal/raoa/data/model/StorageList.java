package ch.bergturbenthal.raoa.data.model;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;

@Data
public class StorageList {
  private String version;
  private final Collection<StorageEntry> clients = new ArrayList<StorageEntry>();
}
