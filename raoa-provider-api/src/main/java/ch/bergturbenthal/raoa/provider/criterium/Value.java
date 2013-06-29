package ch.bergturbenthal.raoa.provider.criterium;

import org.codehaus.jackson.annotate.JsonTypeInfo;

import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public abstract class Value {
}
