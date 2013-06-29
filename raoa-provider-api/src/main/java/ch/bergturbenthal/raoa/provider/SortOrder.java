package ch.bergturbenthal.raoa.provider;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class SortOrder {
	private List<SortOrderEntry> entries = new ArrayList<SortOrderEntry>();

	public void addOrder(final String columnName, final SortOrderEntry.Order order) {
		addOrder(columnName, order, true);
	}

	public void addOrder(final String columnName, final SortOrderEntry.Order order, final boolean nullFirst) {
		final SortOrderEntry newEntry = new SortOrderEntry();
		newEntry.setColumnName(columnName);
		newEntry.setOrder(order);
		newEntry.setNullFirst(nullFirst);
		entries.add(newEntry);
	}
}
