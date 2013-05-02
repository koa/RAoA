package ch.bergturbenthal.raoa.data.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CaptionMutationEntry extends MutationEntry {
  private static final long serialVersionUID = 23443230552119310L;
  private String caption;
}
