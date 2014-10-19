package ch.bergturbenthal.raoa.server.model;

import java.util.SortedSet;
import java.util.TreeSet;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageData {
	private final SortedSet<String> albumList = new TreeSet<>();
	private int gBytesAvailable = Integer.MAX_VALUE;
	private boolean takeAllRepositories = false;
}
