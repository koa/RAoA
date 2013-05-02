package ch.bergturbenthal.raoa.data.model;

import java.io.Serializable;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public abstract class MutationEntry implements Serializable {
  /**
   * 
   */
  private static final long serialVersionUID = 3509283151796921984L;
  private String albumEntryId;
  private String baseVersion;
}
