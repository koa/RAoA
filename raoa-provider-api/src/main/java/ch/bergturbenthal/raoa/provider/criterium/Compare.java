package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Compare extends Criterium {
	public enum Operator {
		CONTAINS, EQUALS, GE, GT, IN, LE, LT, MATCH
	}

	private Value op1;
	private Value op2;
	private Operator operator;

	public Compare() {
		super();
	}

	public Compare(final Value op1, final Value op2, final Operator operator) {
		super();
		this.op1 = op1;
		this.op2 = op2;
		this.operator = operator;
	}

	@Override
	public String toString() {
		switch (operator) {
		case EQUALS:
			return op1 + " = " + op2;
		case GE:
			return op1 + " >= " + op2;
		case GT:
			return op1 + " > " + op2;
		case LE:
			return op1 + " <= " + op2;
		case LT:
			return op1 + " < " + op2;
		case MATCH:
		case CONTAINS:
			return op1 + " =~ " + op2;
		case IN:
			return op1 + " in " + op2;
		}
		return null;
	}
}
