package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TitleImageMutation extends Mutation {
	private static final long serialVersionUID = 8066857172051452576L;
	private String titleImage;
}
