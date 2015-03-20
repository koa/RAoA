package ch.bergturbenthal.raoa.data.model.mutation;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public abstract class AlbumMutation extends Mutation {
	private static final long serialVersionUID = 3886478727043082638L;
	private Date albumLastModified;
}
