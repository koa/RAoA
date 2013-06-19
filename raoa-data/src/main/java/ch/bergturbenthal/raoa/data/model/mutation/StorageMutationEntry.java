/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TODO: add type comment.
 * 
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StorageMutationEntry extends AlbumMutation {
	public static enum StorageMutation {
		ADD, REMOVE
	}

	private static final long serialVersionUID = -6702035328413062621L;
	private StorageMutation mutation;
	private String storage;
}
