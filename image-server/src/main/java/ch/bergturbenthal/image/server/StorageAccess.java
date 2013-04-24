package ch.bergturbenthal.image.server;

import ch.bergturbenthal.image.data.model.StorageList;

public interface StorageAccess {

  StorageList listKnownStorage();

}
