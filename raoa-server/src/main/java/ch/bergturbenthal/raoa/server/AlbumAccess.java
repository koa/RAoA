package ch.bergturbenthal.raoa.server;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;

import ch.bergturbenthal.raoa.data.model.mutation.Mutation;
import ch.bergturbenthal.raoa.server.model.StorageStatistics;

public interface AlbumAccess {
	/**
	 * create a new Album
	 * 
	 * @param pathNames
	 *          whished path-components
	 * @return id of created album
	 */
	Album createAlbum(final String[] pathNames);

	/**
	 * read a found album
	 * 
	 * @param albumId
	 *          id of the album
	 * @return
	 */
	Album getAlbum(final String albumId);

	/**
	 * Unique ID of this Collection
	 * 
	 * @return collection-id
	 */
	String getCollectionId();

	/**
	 * Unique ID of this running instance (Server)
	 * 
	 * @return instance-id
	 */
	String getInstanceId();

	Repository getMetaRepository();

	/**
	 * import files of given directory into the albums.
	 * 
	 * @param importBaseDir
	 */
	void importFiles(final File importBaseDir);

	Map<String, Album> listAlbums();

	void waitForAlbums();

	Collection<String> clientsPerAlbum(final String albumId);

	void registerClient(final String albumId, final String clientId);

	void unRegisterClient(final String albumId, final String clientId);

	void updateMetadata(final String albumId, final Collection<Mutation> updateEntries);

	/**
	 * Read Statistical information
	 * 
	 * @return Statistics
	 */
	StorageStatistics getStatistics();

	/**
	 * @param filename
	 * @param data
	 */
	void importFile(final String filename, final byte[] data);
}
