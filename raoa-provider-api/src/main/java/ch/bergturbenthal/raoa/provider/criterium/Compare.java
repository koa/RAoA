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
		CONTAINS, EQUALS, GE, GT, LE, LT, MATCH
	}

	private Value op1;
	private Value op2;
	private Operator operator;

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
		}
		return null;
	}
}
