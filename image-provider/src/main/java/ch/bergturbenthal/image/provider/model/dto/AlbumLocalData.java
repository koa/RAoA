/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.data.model.MutationEntry;
import ch.bergturbenthal.image.provider.util.ParcelUtil;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumLocalData implements Parcelable {
  public static Parcelable.Creator<AlbumLocalData> CREATOR = new Creator<AlbumLocalData>() {

    @Override
    public AlbumLocalData createFromParcel(final Parcel source) {
      final AlbumLocalData ret = new AlbumLocalData();
      ret.setShouldSync(ParcelUtil.readBoolean(source));
      ret.setSynced(ParcelUtil.readBoolean(source));
      final ArrayList<MutationEntry> mutationList = new ArrayList<MutationEntry>();
      source.readList(mutationList, ret.getClass().getClassLoader());
      ret.setMutations(mutationList);
      return ret;
    }

    @Override
    public AlbumLocalData[] newArray(final int size) {
      return new AlbumLocalData[size];
    }
  };
  private Collection<MutationEntry> mutations;

  private boolean synced;
  private boolean shouldSync;

  @Override
  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final AlbumLocalData other = (AlbumLocalData) obj;
    if (mutations == null) {
      if (other.mutations != null)
        return false;
    } else if (!mutations.equals(other.mutations))
      return false;
    return true;
  }

  /**
   * Returns the mutations.
   * 
   * @return the mutations
   */
  public Collection<MutationEntry> getMutations() {
    return mutations;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mutations == null) ? 0 : mutations.hashCode());
    return result;
  }

  /**
   * Returns the shouldSync.
   * 
   * @return the shouldSync
   */
  public boolean isShouldSync() {
    return shouldSync;
  }

  /**
   * Returns the synced.
   * 
   * @return the synced
   */
  public boolean isSynced() {
    return synced;
  }

  /**
   * Sets the mutations.
   * 
   * @param mutations
   *          the mutations to set
   */
  public void setMutations(final Collection<MutationEntry> mutations) {
    this.mutations = mutations;
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
   * Sets the synced.
   * 
   * @param synced
   *          the synced to set
   */
  public void setSynced(final boolean synced) {
    this.synced = synced;
  }

  @Override
  public String toString() {
    return "MutationList [mutations=" + mutations + "]";
  }

  @Override
  public void writeToParcel(final Parcel dest, final int flags) {
    ParcelUtil.writeBoolean(dest, shouldSync);
    ParcelUtil.writeBoolean(dest, synced);
    dest.writeList(new ArrayList<MutationEntry>(mutations));
  }
}
