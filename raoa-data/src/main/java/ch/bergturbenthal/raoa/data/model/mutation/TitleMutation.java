package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TitleMutation extends AlbumMutation {
	private static final long serialVersionUID = 600906478889035156L;
	private String title;
}
