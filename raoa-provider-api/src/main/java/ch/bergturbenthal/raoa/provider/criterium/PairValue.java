/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PairValue extends Value {

	private Value v1;

	private Value v2;

	public PairValue() {
	}

	public PairValue(final Value v1, final Value v2) {
		this.v1 = v1;
		this.v2 = v2;
	}

	@Override
	public String toString() {
		return "<" + v1 + "," + v2 + ">";
	}
}
