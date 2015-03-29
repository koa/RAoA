package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

 @Data @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type") public abstract class Value {

	public static Value constant(final Object value) {
		return new Constant(value);
	}

	public static Value field(final String fieldName) {
		return new Field(fieldName);
	}

	public static Value pair(final Value first, final Value second) {
		return new PairValue(first, second);
	}
}
