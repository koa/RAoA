package ch.bergturbenthal.raoa.provider.criterium;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Field extends Value {
	private String fieldName;

	@Override
	public String toString() {
		return fieldName;
	}
}
