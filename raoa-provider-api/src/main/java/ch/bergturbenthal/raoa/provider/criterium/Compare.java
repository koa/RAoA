package ch.bergturbenthal.raoa.provider.criterium;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Compare extends Criterium {
	public enum Operator {
		EQUALS, GE, GT, LE, LT, MATCH
	}

	private Value op1;
	private Value op2;
	private Operator operator;

	public static Criterium eq(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.EQUALS);
	}

	public static Criterium ge(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.GE);
	}

	public static Criterium gt(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.GT);
	}

	public static Criterium le(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.LE);
	}

	public static Criterium lt(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.LT);
	}

	public static Criterium match(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.MATCH);
	}

	@Override
	public String toString() {
		switch (operator) {
		case EQUALS:
			return "(" + op1 + " = " + op2 + ")";
		case GE:
			return "(" + op1 + " >= " + op2 + ")";
		case GT:
			return "(" + op1 + " > " + op2 + ")";
		case LE:
			return "(" + op1 + " <= " + op2 + ")";
		case LT:
			return "(" + op1 + " < " + op2 + ")";
		case MATCH:
			return "(" + op1 + " =~ " + op2 + ")";
		}
		return null;
	}
}
