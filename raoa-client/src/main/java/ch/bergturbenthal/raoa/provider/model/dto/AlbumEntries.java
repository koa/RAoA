/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumEntries implements Parcelable {
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

	private final Map<String, AlbumEntryDto> entries = new HashMap<String, AlbumEntryDto>();

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
		final AlbumEntries other = (AlbumEntries) obj;
		if (entries == null) {
			if (other.entries != null)
				return false;
		} else if (!entries.equals(other.entries))
			return false;
		return true;
	}

	/**
	 * Returns the entries.
	 * 
	 * @return the entries
	 */
	public Map<String, AlbumEntryDto> getEntries() {
		return entries;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "AlbumEntries [entries=" + entries + "]";
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeMap(entries);
	}

}
