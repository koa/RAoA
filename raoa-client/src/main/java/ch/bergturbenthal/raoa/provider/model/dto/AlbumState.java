/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumState {
	private boolean isSynced;
	private boolean shouldSync;

	/**
	 * Returns the shouldSync.
	 * 
	 * @return the shouldSync
	 */
	public boolean isShouldSync() {
		return shouldSync;
	}

	/**
	 * Returns the isSynced.
	 * 
	 * @return the isSynced
	 */
	public boolean isSynced() {
		return isSynced;
	}

	/**
	 * Sets the shouldSync.
	 * 
	 * @param shouldSync
	 *          the shouldSync to set
	 */
	public void setShouldSync(final boolean shouldSync) {
		this.shouldSync = shouldSync;
	}

	/**
	 * Sets the isSynced.
	 * 
	 * @param isSynced
	 *          the isSynced to set
	 */
	public void setSynced(final boolean isSynced) {
		this.isSynced = isSynced;
	}

}
