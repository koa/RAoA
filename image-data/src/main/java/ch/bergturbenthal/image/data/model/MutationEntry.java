package ch.bergturbenthal.image.data.model;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public abstract class MutationEntry {
  private String albumEntryId;
  private String baseVersion;
}
