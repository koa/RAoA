package ch.bergturbenthal.raoa.provider;

import lombok.Data;
import lombok.NonNull;

@Data
public class SortOrderEntry {
	public enum Order {
		ASC, DESC
	}

	@NonNull
	private String columnName;
	private boolean nullFirst = true;
	@NonNull
	private Order order;
}
