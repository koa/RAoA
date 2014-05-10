package ch.bergturbenthal.raoa.server.metadata;

import java.util.Date;

import ch.bergturbenthal.raoa.server.model.AlbumEntryData;

public interface MetadataHolder {

	public abstract void fill(AlbumEntryData loadedMetaData);

	public abstract Date readCameraDate();

	public abstract Date readCreateDate();

	public abstract Date readGpsDate();

}