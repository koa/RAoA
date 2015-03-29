package ch.bergturbenthal.raoa.data.model.mutation;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// @Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "mutationType")
@JsonSubTypes({ @JsonSubTypes.Type(value = TitleImageMutation.class, name = "title-image-mutation"),
								@JsonSubTypes.Type(value = TitleMutation.class, name = "title-mutation"),
								@JsonSubTypes.Type(value = CaptionMutationEntry.class, name = "caption-mutation"),
								@JsonSubTypes.Type(value = KeywordMutationEntry.class, name = "keyword-mutation"),
								@JsonSubTypes.Type(value = RatingMutationEntry.class, name = "rating-mutation"),
								@JsonSubTypes.Type(value = StorageMutation.class, name = "storage-mutation") })
public class Mutation implements Serializable {
	private static final long serialVersionUID = 3479098797111789188L;
}