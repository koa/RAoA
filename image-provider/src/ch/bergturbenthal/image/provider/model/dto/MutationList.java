/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.model.dto;

import java.util.Collection;

import ch.bergturbenthal.image.data.model.MutationEntry;

/**
 * TODO: add type comment.
 * 
 */
public class MutationList {
  private Collection<MutationEntry> mutations;

  /**
   * Returns the mutations.
   *
   * @return the mutations
   */
  public Collection<MutationEntry> getMutations() {
    return mutations;
  }

  /**
   * Sets the mutations.
   *
   * @param mutations the mutations to set
   */
  public void setMutations(Collection<MutationEntry> mutations) {
    this.mutations = mutations;
  }
}
