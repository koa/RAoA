package ch.bergturbenthal.raoa.data.model.mutation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KeywordMutationEntry extends EntryMutation {
  public static enum KeywordMutation {
    ADD,
    REMOVE
  }

  private static final long serialVersionUID = 7314544238011292405L;

  private String keyword;
  private KeywordMutation mutation;
}
