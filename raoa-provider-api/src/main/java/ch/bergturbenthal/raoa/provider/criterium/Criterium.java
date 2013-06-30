package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.ObjectMapper;

import ch.bergturbenthal.raoa.provider.criterium.Compare.Operator;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public abstract class Criterium {
	private static final ObjectMapper mapper = new ObjectMapper();

	public static Criterium and(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, ch.bergturbenthal.raoa.provider.criterium.Boolean.Operator.AND);
	}

	public static Criterium decodeString(final String value) {
		try {
			if (value == null || value.length() == 0) {
				return null;
			}
			return mapper.readValue(value, Criterium.class);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot decode string " + value, e);
		}
	}

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
	
	
	public static Criterium contains(final Value op1, final Value op2) {
		return new Compare(op1, op2, Operator.CONTAINS);
	}

	public static Criterium not(final Criterium op) {
		return new Not(op);
	}

	public static Criterium or(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, ch.bergturbenthal.raoa.provider.criterium.Boolean.Operator.OR);
	}

	public static Criterium xor(final Criterium op1, final Criterium op2) {
		return new Boolean(op1, op2, ch.bergturbenthal.raoa.provider.criterium.Boolean.Operator.XOR);
	}

	public String makeString() {
		try {
			return mapper.writeValueAsString(this);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot convert " + this + " to string", e);
		}
	}

}
