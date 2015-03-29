package ch.bergturbenthal.raoa.provider;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

import com.fasterxml.jackson.databind.ObjectMapper;

@Data
public class SortOrder {
	private static final ObjectMapper mapper = new ObjectMapper();

	public static SortOrder decodeString(final String value) {
		try {
			if (value == null || value.length() == 0) {
				return null;
			}
			return mapper.readValue(value, SortOrder.class);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot decode string " + value, e);
		}
	}

	private List<SortOrderEntry> entries = new ArrayList<SortOrderEntry>();

	public void addOrder(final String columnName, final SortOrderEntry.Order order) {
		addOrder(columnName, order, true);
	}

	public void addOrder(final String columnName, final SortOrderEntry.Order order, final boolean nullFirst) {

		final SortOrderEntry newEntry = new SortOrderEntry(columnName, order);
		newEntry.setNullFirst(nullFirst);
		entries.add(newEntry);
	}

	public String makeString() {
		try {
			return mapper.writeValueAsString(this);
		} catch (final Exception e) {
			throw new RuntimeException("Cannot convert " + this + " to string", e);
		}
	}
}
