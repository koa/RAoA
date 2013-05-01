package ch.bergturbenthal.image.data.model;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;

@Data
public class StorageEntry {
  private String storageName;
  private String storageId;
  private Long mBytesAvailable;
  private final Collection<String> albumList = new ArrayList<String>();
}
