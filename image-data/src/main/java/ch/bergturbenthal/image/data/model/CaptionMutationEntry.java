package ch.bergturbenthal.image.data.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CaptionMutationEntry extends MutationEntry {
  private String caption;
}
