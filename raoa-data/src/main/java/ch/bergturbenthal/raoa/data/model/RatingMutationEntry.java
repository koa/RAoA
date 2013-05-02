package ch.bergturbenthal.raoa.data.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RatingMutationEntry extends MutationEntry {
  private static final long serialVersionUID = -6542176308814020110L;
  private Integer rating;
}
