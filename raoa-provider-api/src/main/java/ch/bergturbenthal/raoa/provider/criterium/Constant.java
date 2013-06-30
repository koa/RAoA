package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Constant extends Value {
	private Object value;

	public Constant() {
		super();
	}

	public Constant(final Object value) {
		super();
		this.value = value;
	}

	@Override
	public String toString() {
		return "\"" + value + "\"";
	}
}
