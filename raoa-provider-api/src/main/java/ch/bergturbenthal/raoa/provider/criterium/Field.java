package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Field extends Value {
	private String fieldName;

	public Field() {
		super();
	}

	public Field(final String fieldName) {
		super();
		this.fieldName = fieldName;
	}

	@Override
	public String toString() {
		return fieldName;
	}
}
