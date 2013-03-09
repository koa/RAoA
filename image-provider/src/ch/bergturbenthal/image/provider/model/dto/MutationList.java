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
public class MutationList implements Parcelable {
  public static Parcelable.Creator<MutationList> CREATOR = new Creator<MutationList>() {

    @Override
    public MutationList createFromParcel(final Parcel source) {
      final MutationList ret = new MutationList();
      source.readList(new ArrayList<MutationEntry>(), ret.getClass().getClassLoader());
      return ret;
    }

    @Override
    public MutationList[] newArray(final int size) {
      return new MutationList[size];
    }
  };
  private Collection<MutationEntry> mutations;

  @Override
  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * Returns the mutations.
   * 
   * @return the mutations
   */
  public Collection<MutationEntry> getMutations() {
    return mutations;
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
  public void writeToParcel(final Parcel dest, final int flags) {
    dest.writeList(new ArrayList<MutationEntry>(mutations));
  }
}
