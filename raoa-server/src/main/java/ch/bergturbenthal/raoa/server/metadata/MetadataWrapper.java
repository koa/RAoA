package ch.bergturbenthal.raoa.server.metadata;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.TimeZone;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.bergturbenthal.raoa.server.model.AlbumEntryData;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

public class MetadataWrapper {
	private static class TagId {
		private final Class<? extends Directory> directory;
		private final int tagId;

		public TagId(final Class<? extends Directory> directory, final int tagId) {
			this.directory = directory;
			this.tagId = tagId;
		}
	}

	private static Logger logger = LoggerFactory.getLogger(MetadataWrapper.class);

	private final Metadata metadata;

	private final XmpWrapper xmp;

	public MetadataWrapper(final Metadata metadata) {
		this.metadata = metadata;
		final XmpDirectory xmpDirectory = metadata.getDirectory(XmpDirectory.class);
		if (xmpDirectory != null) {
			xmp = new XmpWrapper(xmpDirectory.getXMPMeta());
		} else {
			xmp = null;
		}
	}

	public void fill(final AlbumEntryData loadedMetaData) {
		loadedMetaData.setExposureTime(readExposureTime());
		loadedMetaData.setFNumber(readFNumber());
		loadedMetaData.setCreationDate(readCreateDate());
		loadedMetaData.setFocalLength(readFocalLength());
		loadedMetaData.setIso(readIso());
		loadedMetaData.setKeywords(readKeywords());
		loadedMetaData.setRating(readRating());
		loadedMetaData.setCameraMake(readCameraMake());
		loadedMetaData.setCameraModel(readCameraModel());
		loadedMetaData.setCaption(readCaption());
	}

	public Date readCreateDate() {
		final Date gpsDate = readGpsDate();
		if (gpsDate != null)
			// logger.info("GPS-Date: " + gpsDate);
			return gpsDate;
		for (final TagId index : Arrays.asList(new TagId(ExifIFD0Directory.class, ExifIFD0Directory.TAG_DATETIME))) {
			final Date date = readDate(index.directory, index.tagId);
			if (date != null)
				// logger.info(index.directory.getSimpleName() + ":" + index.tagId +
				// ": " + date);
				return date;
		}
		return null;
	}

	private String readCameraMake() {
		return StringUtils.trimToNull(readString(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MAKE));
	}

	private String readCameraModel() {
		return StringUtils.trimToNull(readString(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL));
	}

	private String readCaption() {
		if (xmp != null) {
			final String description = xmp.readDescription();
			if (description != null)
				return description;
		}
		return readString(IptcDirectory.class, IptcDirectory.TAG_CAPTION);
	}

	private Date readDate(final Class<? extends Directory> directory, final int tag) {
		if (metadata.containsDirectory(directory)) {
			final Directory directory2 = metadata.getDirectory(directory);
			if (directory2.containsTag(tag))
				return directory2.getDate(tag);
		}
		return null;
	}

	private Double readDouble(final Class<? extends Directory> directory, final int tag) {
		final Directory directory2 = metadata.getDirectory(directory);
		if (directory2 == null)
			return null;
		return directory2.getDoubleObject(tag);

	}

	private Double readExposureTime() {
		return readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
	}

	private Double readFNumber() {
		final Double doubleObject = readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FNUMBER);
		if (doubleObject == null)
			return null;
		return doubleObject;
		// return Double.valueOf(Math.exp(doubleObject.byteValue() * Math.log(2) *
		// 0.5));
		// return
		// Double.valueOf(PhotographicConversions.apertureToFStop(doubleObject.byteValue()));
	}

	private Double readFocalLength() {
		return readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
	}

	private Date readGpsDate() {
		try {
			if (!metadata.containsDirectory(GpsDirectory.class))
				return null;
			final Directory directory = metadata.getDirectory(GpsDirectory.class);
			if (!directory.containsTag(GpsDirectory.TAG_GPS_TIME_STAMP))
				return null;
			final int[] time = directory.getIntArray(7);
			final String date = directory.getString(29);
			final Object[] values = new MessageFormat("{0,number}:{1,number}:{2,number}").parse(date);
			final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			calendar.set(((Number) values[0]).intValue(), ((Number) values[1]).intValue() - 1, ((Number) values[2]).intValue(), time[0], time[1], time[2]);
			return calendar.getTime();
		} catch (final ParseException e) {
			logger.warn("Cannot read Gps-Date", e);
			return null;
		}
	}

	private Integer readInteger(final Class<? extends Directory> directory, final int tag) {
		final Directory directory2 = metadata.getDirectory(directory);
		if (directory2 == null)
			return null;
		return directory2.getInteger(tag);

	}

	private Integer readIso() {
		return readInteger(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
	}

	private Collection<String> readKeywords() {
		final Collection<String> ret = new LinkedHashSet<>();
		final IptcDirectory iptcDirectory = metadata.getDirectory(IptcDirectory.class);
		if (iptcDirectory != null) {
			final String[] iptcKeywords = iptcDirectory.getStringArray(IptcDirectory.TAG_KEYWORDS);
			if (iptcKeywords != null) {
				ret.addAll(Arrays.asList(iptcKeywords));
			}
		}
		if (xmp != null) {
			ret.addAll(xmp.readKeywords());
		}
		return ret;
	}

	private Integer readRating() {
		if (xmp != null)
			return xmp.readRating();
		else
			return null;
	}

	private String readString(final Class<? extends Directory> directory, final int tag) {
		if (metadata.containsDirectory(directory)) {
			final Directory directory2 = metadata.getDirectory(directory);
			if (directory2.containsTag(tag))
				return directory2.getString(tag);
		}
		return null;
	}

}
