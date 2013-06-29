package ch.bergturbenthal.raoa.provider;

import lombok.Data;

@Data
public class SortOrderEntry {
	public enum Order {
		ASC, DESC
	}

	private String columnName;
	private boolean nullFirst = true;
	private Order order;
}
