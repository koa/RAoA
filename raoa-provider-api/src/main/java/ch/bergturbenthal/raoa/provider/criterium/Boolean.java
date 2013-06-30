package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Boolean extends Criterium {
	public enum Operator {
		AND, OR, XOR
	}

	private Criterium op1;
	private Criterium op2;
	private Operator operator;

	public Boolean() {
		super();
	}

	public Boolean(final Criterium op1, final Criterium op2, final Operator operator) {
		super();
		this.op1 = op1;
		this.op2 = op2;
		this.operator = operator;
	}

	@Override
	public String toString() {
		switch (operator) {
		case AND:
			return "(" + op1 + ") && (" + op2 + ")";
		case OR:
			return "(" + op1 + ") || (" + op2 + ")";
		case XOR:
			return "(" + op1 + ") ^ (" + op2 + ")";
		}
		return null;
	}
}
