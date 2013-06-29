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
