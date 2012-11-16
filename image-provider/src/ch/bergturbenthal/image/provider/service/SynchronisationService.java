package ch.bergturbenthal.image.provider.service;

import java.io.File;

public interface SynchronisationService {
  File getLoadedThumbnail(int thumbnailId);
}
