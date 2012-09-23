package ch.bergturbenthal.image.server;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

public class DateParser {
  @Test
  public void testWriteAndRead() {
    final long now = System.currentTimeMillis();
    final String nowStr = ISODateTimeFormat.dateTime().print(now);
    System.out.println(nowStr);
    final DateTime dateTime = ISODateTimeFormat.dateTimeParser().parseDateTime(nowStr);
    System.out.println(dateTime);
    System.out.println(now - dateTime.getMillis());

    // final String localTimeString = nowStr.substring(0, nowStr.length() - 6);
    final String localTimeString = "2012-07-29";
    System.out.println(localTimeString);
    final DateTimeFormatter dateTimeParser = ISODateTimeFormat.dateTimeParser();
    final DateTime localeDateTime = dateTimeParser.parseDateTime(localTimeString);
    System.out.println(localeDateTime);

    System.out.println(now - localeDateTime.getMillis());
  }
}
