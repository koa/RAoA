package ch.bergturbenthal.image.data.model;

import java.io.Serializable;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public abstract class MutationEntry implements Serializable {
  private String albumEntryId;
  private String baseVersion;
}
