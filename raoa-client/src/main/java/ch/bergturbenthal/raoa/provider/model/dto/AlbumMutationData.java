/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;

/**
 * TODO: add type comment.
 * 
 */
public class AlbumMutationData implements Parcelable {
	public static Parcelable.Creator<AlbumMutationData> CREATOR = new Creator<AlbumMutationData>() {

		@Override
		public AlbumMutationData createFromParcel(final Parcel source) {
			final AlbumMutationData ret = new AlbumMutationData();
			final ArrayList<Mutation> mutationList = new ArrayList<Mutation>();
			source.readList(mutationList, ret.getClass().getClassLoader());
			ret.setMutations(mutationList);
			return ret;
		}

		@Override
		public AlbumMutationData[] newArray(final int size) {
			return new AlbumMutationData[size];
		}
	};
	private Collection<Mutation> mutations = new ArrayList<Mutation>();

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final AlbumMutationData other = (AlbumMutationData) obj;
		if (mutations == null) {
			if (other.mutations != null) {
				return false;
			}
		} else if (!mutations.equals(other.mutations)) {
			return false;
		}
		return true;
	}

	/**
	 * Returns the mutations.
	 * 
	 * @return the mutations
	 */
	public Collection<Mutation> getMutations() {
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
	public void setMutations(final Collection<Mutation> mutations) {
		this.mutations = mutations;
	}

	@Override
	public String toString() {
		return "MutationList [mutations=" + mutations + "]";
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeList(new ArrayList<Mutation>(mutations));
	}
}
