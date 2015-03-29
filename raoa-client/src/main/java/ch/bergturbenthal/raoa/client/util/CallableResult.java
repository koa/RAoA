package ch.bergturbenthal.raoa.client.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(suppressConstructorProperties = true, access = AccessLevel.PRIVATE)
public class CallableResult<V> {
	public static <V> CallableResult<V> exceptionResult(final Exception ex) {
		return new CallableResult<V>(ex, null);
	}

	public static <V> CallableResult<V> valueResult(final V value) {
		return new CallableResult<V>(null, value);
	}

	private final Exception	exeption;
	private final V	        value;
}
