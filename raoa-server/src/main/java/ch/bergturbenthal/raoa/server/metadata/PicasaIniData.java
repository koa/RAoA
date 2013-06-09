package ch.bergturbenthal.raoa.server.metadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Cleanup;
import lombok.Data;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class PicasaIniData {
	private static final Logger logger = LoggerFactory.getLogger(PicasaIniData.class);
	private static final Pattern sectionPattern = Pattern.compile("\\[([a-zA-Z0-9\\.-_]+)\\]");

	private final Map<String, PicasaIniEntryData> entries = new HashMap<>();
	private String name;

	public static PicasaIniData parseIniFile(final File baseDir) {
		final PicasaIniData ret = new PicasaIniData();

		final File[] foundFiles = baseDir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(final File dir, final String name) {
				final String lowerCaseName = name.toLowerCase();
				return lowerCaseName.equals("picasa.ini") || lowerCaseName.equals(".picasa.ini");
			}
		});
		final Map<String, PicasaIniEntryData> entryMap = ret.entries;
		for (final File file : foundFiles) {
			try {
				@Cleanup
				final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
				String currentGroup = null;
				while (true) {
					final String line = StringUtils.trim(reader.readLine());
					if (line == null) {
						break;
					}
					final String nonCommentData = line.split("#", 2)[0].trim();
					if (nonCommentData.length() == 0) {
						continue;
					}
					final Matcher sectionMatcher = sectionPattern.matcher(nonCommentData);
					if (sectionMatcher.matches()) {
						currentGroup = sectionMatcher.group(1);
						continue;
					}
					if (currentGroup == null) {
						continue;
					}
					final String[] keyValue = nonCommentData.split("=", 2);
					if (keyValue.length != 2) {
						continue;
					}
					final String key = keyValue[0].trim();
					final String value = keyValue[1].trim();
					if (currentGroup.equals("Picasa")) {
						if (key.equals("name")) {
							ret.name = value;
						}
					} else if (currentGroup.equals("Contacts")) {
						continue;
					} else {
						if (key.equals("star") && value.equals("yes")) {
							getCurrentData(entryMap, currentGroup).setStar(true);
						} else if (key.equals("caption")) {
							getCurrentData(entryMap, currentGroup).setCaption(value);
						} else if (key.equals("keywords")) {
							getCurrentData(entryMap, key).appendKeywords(value);
						}
					}
				}
			} catch (final IOException e) {
				logger.warn("Cannot parse " + file, e);
			}
		}

		return ret;
	}

	private static final PicasaIniEntryData getCurrentData(final Map<String, PicasaIniEntryData> ret, final String currentGroup) {
		final PicasaIniEntryData currentValue = ret.get(currentGroup);
		if (currentValue != null)
			return currentValue;
		final PicasaIniEntryData newValue = new PicasaIniEntryData();
		ret.put(currentGroup, newValue);
		return newValue;
	}

}
