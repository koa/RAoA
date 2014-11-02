/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.model.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

import lombok.Value;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;

/**
 * TODO: add type comment.
 *
 */
@Value
public class AlbumMutationData implements Serializable {

	private Collection<Mutation> mutations;

	public AlbumMutationData(final Collection<Mutation> mutations) {
		this.mutations = Collections.unmodifiableCollection(mutations);
	}

}
