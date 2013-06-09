package ch.bergturbenthal.raoa.server.metadata;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.options.PropertyOptions;
import com.adobe.xmp.properties.XMPProperty;

public class XmpWrapper {
	private final XMPMeta meta;

	public XmpWrapper(final XMPMeta meta) {
		this.meta = meta;

	}

	public void addKeyword(final String keyword) {
		final Collection<String> existingKeywords = readKeywords();
		if (!existingKeywords.contains(keyword)) {
			appendStringEntry(XMPConst.NS_DC, "subject", keyword);
		}
	}

	public String readDescription() {
		try {
			final int entryCount = meta.countArrayItems(XMPConst.NS_DC, "description");
			if (entryCount < 1)
				return null;
			final XMPProperty arrayItem = meta.getArrayItem(XMPConst.NS_DC, "description", 1);
			if (arrayItem == null)
				return null;
			return arrayItem.getValue();
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot read Description", e);
		}
	}

	public Collection<String> readKeywords() {
		final Collection<String> ret = new LinkedHashSet<>();
		ret.addAll(Arrays.asList(readStringArray(XMPConst.NS_IPTCCORE, "Keywords")));
		ret.addAll(Arrays.asList(readStringArray(XMPConst.NS_DC, "subject")));
		return ret;
	}

	public Integer readRating() {
		try {
			return meta.getPropertyInteger(XMPConst.NS_XMP, "Rating");
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot read rating", e);
		}
	}

	public String[] readStringArray(final String propertyNs, final String property) {
		try {
			final int itemCount = meta.countArrayItems(propertyNs, property);
			final String[] ret = new String[itemCount];
			for (int i = 0; i < itemCount; i++) {
				final XMPProperty item = meta.getArrayItem(propertyNs, property, i + 1);
				ret[i] = item.getValue();
			}
			return ret;
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot read Property " + propertyNs + ":" + property, e);
		}
	}

	public void removeKeyword(final String keyword) {
		try {
			for (final String[] pair : new String[][] { new String[] { XMPConst.NS_IPTCCORE, "Keywords" }, new String[] { XMPConst.NS_DC, "subject" } }) {
				final String ns = pair[0];
				final String property = pair[1];
				for (int i = 1; i <= meta.countArrayItems(ns, property);) {
					final XMPProperty arrayItem = meta.getArrayItem(ns, property, i);
					if (arrayItem.getValue().equals(keyword)) {
						meta.deleteArrayItem(ns, property, i);
					} else {
						i += 1;
					}
				}
			}
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot remove keyword \"" + keyword + "\"", e);
		}
	}

	public void setRating(final Integer rating) {
		try {
			if (rating == null) {
				meta.deleteProperty(XMPConst.NS_XMP, "Rating");
			} else {
				meta.setPropertyInteger(XMPConst.NS_XMP, "Rating", rating.intValue());
			}
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot update rating", e);
		}
	}

	public void updateDescription(final String description) {
		try {
			if (meta.countArrayItems(XMPConst.NS_DC, "description") == 0) {
				meta.appendArrayItem(XMPConst.NS_DC, "description", description);
			} else {
				meta.setArrayItem(XMPConst.NS_DC, "description", 1, description);
			}
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot write Description", e);
		}
	}

	private void appendStringEntry(final String propertyNs, final String property, final String keyword) {
		try {
			meta.appendArrayItem(propertyNs, property, new PropertyOptions(PropertyOptions.ARRAY), keyword, null);
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot append entry " + propertyNs + ":" + keyword, e);
		}
	}
}
