package ch.bergturbenthal.raoa.server.model.gallery;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GalleryEntry {
	private Collection<CategoryEntry> categories;
	@JsonProperty("eHeight")
	private double eHeight;
	private String enlarged;
	@JsonProperty("eWidth")
	private double eWidth;
	@JsonProperty("tHeight")
	private double tHeight;
	private String thumbnail;
	private String title;
	@JsonProperty("tWidth")
	private double tWidth;
}
