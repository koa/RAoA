package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Not extends Criterium {
	private Criterium criterium;

	public Not() {
		super();
	}

	public Not(final Criterium criterium) {
		super();
		this.criterium = criterium;
	}

	@Override
	public String toString() {
		return "!(" + criterium + ")";
	}
}
