package ch.bergturbenthal.raoa.data.model.mutation;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AlbumMutation extends Mutation {
	private static final long serialVersionUID = 3886478727043082638L;
	private Date albumLastModified;
}
