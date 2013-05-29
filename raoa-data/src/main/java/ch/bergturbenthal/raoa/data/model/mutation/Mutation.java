package ch.bergturbenthal.raoa.data.model.mutation;

import java.io.Serializable;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "mutationType")
public class Mutation implements Serializable {
	private static final long serialVersionUID = 3479098797111789188L;
}