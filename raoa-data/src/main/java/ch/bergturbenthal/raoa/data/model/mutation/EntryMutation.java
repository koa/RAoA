package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public abstract class EntryMutation extends Mutation {
	private static final long serialVersionUID = 3509283151796921984L;
	private String albumEntryId;
	private String baseVersion;
}
