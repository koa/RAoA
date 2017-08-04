package ch.bergturbenthal.raoa.server.model.gallery;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
public class CategoryEntry {
	private int id;
	@JsonProperty("photo_count")
	private int photoCount;
	private int title;
}
