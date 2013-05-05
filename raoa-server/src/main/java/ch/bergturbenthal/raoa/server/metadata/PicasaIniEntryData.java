package ch.bergturbenthal.raoa.server.metadata;

import java.util.Collection;
import java.util.LinkedHashSet;

import lombok.Data;

@Data
public class PicasaIniEntryData {

	private String caption;
	private Collection<String> keywords = new LinkedHashSet<>();
	private boolean star = false;

	public void appendKeywords(final String commaSeparatedKeywords) {
		for (final String word : commaSeparatedKeywords.split(",")) {
			keywords.add(word.trim());
		}
	}
}
