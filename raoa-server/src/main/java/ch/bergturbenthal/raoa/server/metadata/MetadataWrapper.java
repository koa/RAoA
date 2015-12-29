package ch.bergturbenthal.raoa.server.metadata;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

import ch.bergturbenthal.raoa.server.model.AlbumEntryData;

public class MetadataWrapper implements MetadataHolder {
	private static class TagId {
		private final Class<? extends Directory> directory;
		private final int tagId;

		public TagId(final Class<? extends Directory> directory, final int tagId) {
			this.directory = directory;
			this.tagId = tagId;
		}
	}

	private static Logger logger = LoggerFactory.getLogger(MetadataWrapper.class);

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmedValue = value.trim();
		if (trimmedValue.isEmpty()) {
			return null;
		}
		return trimmedValue;
	}

	private final Metadata metadata;

	private final XmpWrapper xmp;

	public MetadataWrapper(final Metadata metadata) {
		this.metadata = metadata;
		final Collection<XmpDirectory> directoriesOfType = metadata.getDirectoriesOfType(XmpDirectory.class);
		if (directoriesOfType != null && !directoriesOfType.isEmpty()) {
			final XmpDirectory xmpDirectory = directoriesOfType.iterator().next();
			xmp = new XmpWrapper(xmpDirectory.getXMPMeta());
		} else {
			xmp = null;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.server.metadata.MetadataHolder#fill(ch.bergturbenthal.raoa.server.model.AlbumEntryData)
	 */
	@Override
	public void fill(final AlbumEntryData loadedMetaData) {
		loadedMetaData.setExposureTime(readExposureTime());
		loadedMetaData.setFNumber(readFNumber());
		loadedMetaData.setCameraDate(readCameraDate());
		loadedMetaData.setGpsDate(readGpsDate());
		loadedMetaData.setFocalLength(readFocalLength());
		loadedMetaData.setIso(readIso());
		loadedMetaData.setKeywords(readKeywords());
		loadedMetaData.setRating(readRating());
		loadedMetaData.setCameraMake(readCameraMake());
		loadedMetaData.setCameraModel(readCameraModel());
		loadedMetaData.setCaption(readCaption());
		loadedMetaData.setCameraSerial(readCameraSerial());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.server.metadata.MetadataHolder#readCameraDate()
	 */
	@Override
	public Date readCameraDate() {
		final Date date = readDate(ExifIFD0Directory.class, ExifIFD0Directory.TAG_DATETIME);
		if (date != null) {
			return date;
		}
		return readDate(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
	}

	private String readCameraMake() {
		return trimToNull(readString(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MAKE));
	}

	private String readCameraModel() {
		return trimToNull(readString(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL));
	}

	private String readCameraSerial() {
		for (final TagId tag : new TagId[] {	new TagId(NikonType2MakernoteDirectory.class, NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER_2),
																					new TagId(NikonType2MakernoteDirectory.class, NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER),
																					new TagId(CanonMakernoteDirectory.class, CanonMakernoteDirectory.TAG_CANON_SERIAL_NUMBER) }) {
			final String serial = trimToNull(readString(tag.directory, tag.tagId));
			if (serial != null) {
				return serial;
			}
		}

		return null;
	}

	private String readCaption() {
		if (xmp != null) {
			final String description = xmp.readDescription();
			if (description != null) {
				return description;
			}
		}
		return readString(IptcDirectory.class, IptcDirectory.TAG_CAPTION);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.server.metadata.MetadataHolder#readCreateDate()
	 */
	@Override
	public Date readCreateDate() {
		final Date gpsDate = readGpsDate();
		if (gpsDate != null) {
			return gpsDate;
		}
		return readCameraDate();
	}

	private Date readDate(final Class<? extends Directory> directory, final int tag) {
		final Collection<? extends Directory> directoriesOfType = metadata.getDirectoriesOfType(directory);
		if (directoriesOfType != null) {
			for (final Directory directory2 : directoriesOfType) {
				if (directory2.containsTag(tag)) {
					return directory2.getDate(tag);
				}

			}
		}
		return null;
	}

	private Double readDouble(final Class<? extends Directory> directory, final int tag) {
		final Collection<? extends Directory> directoriesOfType = metadata.getDirectoriesOfType(directory);
		if (directoriesOfType != null) {
			for (final Directory directory2 : directoriesOfType) {
				if (directory2.containsTag(tag)) {
					return directory2.getDoubleObject(tag);
				}

			}
		}
		return null;
	}

	private Double readExposureTime() {
		return readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
	}

	private Double readFNumber() {
		final Double doubleObject = readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FNUMBER);
		if (doubleObject == null) {
			return null;
		}
		return doubleObject;
		// return Double.valueOf(Math.exp(doubleObject.byteValue() * Math.log(2) *
		// 0.5));
		// return
		// Double.valueOf(PhotographicConversions.apertureToFStop(doubleObject.byteValue()));
	}

	private Double readFocalLength() {
		return readDouble(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.raoa.server.metadata.MetadataHolder#readGpsDate()
	 */
	@Override
	public Date readGpsDate() {
		final Collection<GpsDirectory> gpsDirectories = metadata.getDirectoriesOfType(GpsDirectory.class);
		if (gpsDirectories == null) {
			return null;
		}
		for (final GpsDirectory gpsDirectory : gpsDirectories) {
			try {
				if (!gpsDirectory.containsTag(GpsDirectory.TAG_TIME_STAMP)) {
					continue;
				}
				final int[] time = gpsDirectory.getIntArray(7);
				final String date = gpsDirectory.getString(29);
				final Object[] values = new MessageFormat("{0,number}:{1,number}:{2,number}").parse(date);
				final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
				calendar.set(((Number) values[0]).intValue(), ((Number) values[1]).intValue() - 1, ((Number) values[2]).intValue(), time[0], time[1], time[2]);
				return calendar.getTime();
			} catch (final ParseException e) {
				logger.warn("Cannot read Gps-Date", e);
				continue;
			}
		}
		return null;
	}

	private Integer readInteger(final Class<? extends Directory> directory, final int tag) {
		final Collection<? extends Directory> directoriesOfType = metadata.getDirectoriesOfType(directory);
		if (directoriesOfType != null) {
			for (final Directory directory2 : directoriesOfType) {
				if (directory2.containsTag(tag)) {
					return directory2.getInteger(tag);
				}

			}
		}
		return null;
	}

	private Integer readIso() {
		return readInteger(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
	}

	private Collection<String> readKeywords() {
		final Collection<String> ret = new LinkedHashSet<>();
		final Collection<IptcDirectory> iptcDirectories = metadata.getDirectoriesOfType(IptcDirectory.class);
		if (iptcDirectories != null) {
			for (final IptcDirectory iptcDirectory : iptcDirectories) {
				if (iptcDirectory != null) {
					final String[] iptcKeywords = iptcDirectory.getStringArray(IptcDirectory.TAG_KEYWORDS);
					if (iptcKeywords != null) {
						ret.addAll(Arrays.asList(iptcKeywords));
					}
				}
				if (xmp != null) {
					ret.addAll(xmp.readKeywords());
				}
			}
		}
		return ret;
	}

	private Integer readRating() {
		if (xmp != null) {
			return xmp.readRating();
		} else {
			return null;
		}
	}

	private String readString(final Class<? extends Directory> directory, final int tag) {
		final Collection<? extends Directory> directoriesOfType = metadata.getDirectoriesOfType(directory);
		if (directoriesOfType != null) {
			for (final Directory directory2 : directoriesOfType) {
				if (directory2.containsTag(tag)) {
					return directory2.getString(tag);
				}

			}
		}
		return null;
	}

}
