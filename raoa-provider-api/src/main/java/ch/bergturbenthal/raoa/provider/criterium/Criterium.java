package ch.bergturbenthal.raoa.provider.criterium;

import lombok.Data;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.ObjectMapper;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public abstract class Criterium {
	private static final ObjectMapper mapper = new ObjectMapper();

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

	public String makeString() {
		try {
			return mapper.writeValueAsString(this);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot convert " + this + " to string", e);
		}
	}

}
