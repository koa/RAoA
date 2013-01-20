package ch.bergturbenthal.image.data.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KeywordMutationEntry extends MutationEntry {
  public static enum KeywordMutation {
    ADD,
    REMOVE
  }

  private String keyword;
  private KeywordMutation mutation;
}
