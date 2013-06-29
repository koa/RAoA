package ch.bergturbenthal.raoa.provider.criterium;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Boolean extends Criterium {
	public enum Operator {
		AND, OR, XOR
	}

	private Criterium op1;
	private Criterium op2;
	private Operator operator;

	public static Criterium and(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, Operator.AND);
	}

	public static Criterium or(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, Operator.OR);
	}

	public static Criterium xor(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, Operator.XOR);
	}

	@Override
	public String toString() {
		switch (operator) {
		case AND:
			return "(" + op1 + " && " + op2 + ")";
		case OR:
			return "(" + op1 + " || " + op2 + ")";
		case XOR:
			return "(" + op1 + " ^ " + op2 + ")";
		}
		return null;
	}
}
