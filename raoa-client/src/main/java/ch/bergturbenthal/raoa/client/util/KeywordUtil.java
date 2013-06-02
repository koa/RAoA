package ch.bergturbenthal.raoa.client.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentResolver;
import android.database.Cursor;
import ch.bergturbenthal.raoa.provider.Client;

public class KeywordUtil {
	public static List<String> getKnownKeywords(final ContentResolver contextResolver) {
		final Cursor cursor = contextResolver.query(Client.KEYWORDS_URI, new String[] { Client.KeywordEntry.KEYWORD, Client.KeywordEntry.COUNT }, null, null, null);
		try {
			return readOrderedKeywordsFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public static ArrayList<String> orderKeywordsByFrequent(final Map<String, Integer> countOrder) {
		final ArrayList<String> keyWords = new ArrayList<String>(countOrder.keySet());
		Collections.sort(keyWords, new Comparator<String>() {
			@Override
			public int compare(final String lhs, final String rhs) {
				return -countOrder.get(lhs).compareTo(countOrder.get(rhs));
			}
		});
		return keyWords;
	}

	private static List<String> readOrderedKeywordsFromCursor(final Cursor data) {
		if (data == null || !data.moveToFirst()) {
			return Collections.emptyList();
		}
		final Map<String, Integer> countOrder = new HashMap<String, Integer>();
		do {
			final String keyword = data.getString(0);
			final int count = data.getInt(1);
			countOrder.put(keyword, Integer.valueOf(count));
		} while (data.moveToNext());
		return orderKeywordsByFrequent(countOrder);
	}
}
