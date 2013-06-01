/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.raoa.server.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Statistical Data about a storage
 * 
 */
@Data
public class StorageStatistics {
	private final Map<String, Integer> keywordCount = new HashMap<>();
}
