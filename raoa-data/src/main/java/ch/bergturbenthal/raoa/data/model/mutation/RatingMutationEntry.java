package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RatingMutationEntry extends EntryMutation {
  private static final long serialVersionUID = -6542176308814020110L;
  private Integer rating;
}
