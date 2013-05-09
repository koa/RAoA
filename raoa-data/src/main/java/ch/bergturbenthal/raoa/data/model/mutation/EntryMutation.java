package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class EntryMutation extends Mutation {
	private static final long serialVersionUID = 3509283151796921984L;
	private String albumEntryId;
	private String baseVersion;
}
