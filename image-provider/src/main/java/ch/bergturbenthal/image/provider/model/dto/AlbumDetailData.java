/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.data.model.MutationEntry;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumDetailData implements Parcelable {
  public static Parcelable.Creator<AlbumDetailData> CREATOR = new Creator<AlbumDetailData>() {

    @Override
    public AlbumDetailData createFromParcel(final Parcel source) {
      final AlbumDetailData ret = new AlbumDetailData();
      final ArrayList<MutationEntry> mutationList = new ArrayList<MutationEntry>();
      source.readList(mutationList, ret.getClass().getClassLoader());
      ret.setMutations(mutationList);
      return ret;
    }

    @Override
    public AlbumDetailData[] newArray(final int size) {
      return new AlbumDetailData[size];
    }
  };
  private Collection<MutationEntry> mutations;

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
    final AlbumDetailData other = (AlbumDetailData) obj;
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
   * Sets the mutations.
   * 
   * @param mutations
   *          the mutations to set
   */
  public void setMutations(final Collection<MutationEntry> mutations) {
    this.mutations = mutations;
  }

  @Override
  public String toString() {
    return "MutationList [mutations=" + mutations + "]";
  }

  @Override
  public void writeToParcel(final Parcel dest, final int flags) {
    dest.writeList(new ArrayList<MutationEntry>(mutations));
  }
}
