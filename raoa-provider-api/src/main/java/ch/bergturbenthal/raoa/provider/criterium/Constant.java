package ch.bergturbenthal.raoa.provider.criterium;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Constant extends Value {
	private Object value;

	@Override
	public String toString() {
		return "\"" + value + "\"";
	}
}
