/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.data.model;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;
import ch.bergturbenthal.raoa.data.model.mutation.Mutation;

@Data
public class UpdateMetadataRequest {
	private Collection<Mutation> mutationEntries = new ArrayList<Mutation>();
}
