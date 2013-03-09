/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumEntries implements Parcelable {
  private Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();

  public static final Parcelable.Creator<AlbumEntries> CREATOR = new Parcelable.Creator<AlbumEntries>() {

    @Override
    public AlbumEntries createFromParcel(final Parcel source) {
      final AlbumEntries ret = new AlbumEntries();
      source.readMap(ret.entries, ret.getClass().getClassLoader());
      return ret;
    }

    @Override
    public AlbumEntries[] newArray(final int size) {
      return new AlbumEntries[size];
    }
  };

  @Override
  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * Returns the entries.
   * 
   * @return the entries
   */
  public Map<String, AlbumEntryDto> getEntries() {
    return entries;
  }

  /**
   * Sets the entries.
   * 
   * @param entries
   *          the entries to set
   */
  public void setEntries(final Map<String, AlbumEntryDto> entries) {
    this.entries = entries;
  }

  @Override
  public void writeToParcel(final Parcel dest, final int flags) {
    dest.writeMap(entries);
  }

}
