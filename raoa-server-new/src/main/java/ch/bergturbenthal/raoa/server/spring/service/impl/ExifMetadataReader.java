package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.TimeZone;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.lib.ObjectLoader;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.model.AlbumEntryMetadata.AlbumEntryMetadataBuilder;
import ch.bergturbenthal.raoa.server.spring.service.MetadataReader;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.properties.XMPProperty;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;

@Slf4j
@Service
public class ExifMetadataReader implements MetadataReader {
	@Value
	private static class TagId {
		private final Class<? extends Directory> directory;
		private final int tagId;
	}

	private static <T> Iterable<T> emptyIfNull(final Iterable<T> iterable) {
		if (iterable == null) {
			return Collections.emptyList();
		}
		return iterable;
	}

	private static Collection<String> readAllElements(final XmpDirectory directory, final String ns, final String element) {
		final XMPMeta meta = directory.getXMPMeta();
		try {
			final int entryCount = meta.countArrayItems(ns, element);
			if (entryCount < 1) {
				return Collections.emptyList();
			}

			final String[] ret = new String[entryCount];
			for (int i = 0; i < entryCount; i++) {
				final XMPProperty item = meta.getArrayItem(ns, element, i + 1);
				ret[i] = item.getValue();
			}
			return Arrays.asList(ret);
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot read Description", e);
		}
	}

	private static Date readCameraDate(final Metadata metadata) {
		final Date date = readDate(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_DATETIME);
		if (date != null) {
			return date;
		}
		return readDate(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
	}

	private static String readCameraMake(final Metadata metadata) {
		return trimToNull(readString(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MAKE));
	}

	private static String readCameraModel(final Metadata metadata) {
		return trimToNull(readString(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL));
	}

	private static String readCameraSerial(final Metadata metadata) {
		for (final TagId tag : new TagId[] { new TagId(NikonType2MakernoteDirectory.class, NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER_2),
																				new TagId(NikonType2MakernoteDirectory.class, NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER),
																				new TagId(CanonMakernoteDirectory.class, CanonMakernoteDirectory.TAG_CANON_SERIAL_NUMBER) }) {
			final String serial = trimToNull(readString(metadata, tag.directory, tag.tagId));
			if (serial != null) {
				return serial;
			}
		}

		return null;
	}

	private static String readCaption(final Metadata metadata) {
		for (final XmpDirectory directory : emptyIfNull(metadata.getDirectoriesOfType(XmpDirectory.class))) {
			final String foundResult = readFirstElement(directory, XMPConst.NS_DC, "description");
			if (foundResult != null) {
				return foundResult;
			}
		}

		return readString(metadata, IptcDirectory.class, IptcDirectory.TAG_CAPTION);
	}

	private static Date readDate(final Metadata metadata, final Class<? extends Directory> directory, final int tag) {
		for (final Directory dir : emptyIfNull(metadata.getDirectoriesOfType(directory))) {
			if (dir.containsTag(tag)) {
				return dir.getDate(tag);
			}
		}
		return null;
	}

	private static Double readDouble(final Metadata metadata, final Class<? extends Directory> directory, final int tag) {
		for (final Directory dir : emptyIfNull(metadata.getDirectoriesOfType(directory))) {
			if (dir.containsTag(tag)) {
				return dir.getDoubleObject(tag);
			}
		}
		return null;
	}

	private static Double readExposureTime(final Metadata metadata) {
		return readDouble(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
	}

	private static String readFirstElement(final XmpDirectory directory, final String ns, final String element) {
		final XMPMeta meta = directory.getXMPMeta();
		try {
			final int entryCount = meta.countArrayItems(ns, element);
			if (entryCount < 1) {
				return null;
			}
			final XMPProperty arrayItem = meta.getArrayItem(ns, element, 1);
			if (arrayItem == null) {
				return null;
			}
			return arrayItem.getValue();
		} catch (final XMPException e) {
			throw new RuntimeException("Cannot read Description", e);
		}
	}

	private static Double readFNumber(final Metadata metadata) {
		final Double doubleObject = readDouble(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FNUMBER);
		if (doubleObject == null) {
			return null;
		}
		return doubleObject;
		// return Double.valueOf(Math.exp(doubleObject.byteValue() * Math.log(2) *
		// 0.5));
		// return
		// Double.valueOf(PhotographicConversions.apertureToFStop(doubleObject.byteValue()));
	}

	private static Double readFocalLength(final Metadata metadata) {
		return readDouble(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
	}

	private static Date readGpsDate(final Metadata metadata) {
		try {
			for (final GpsDirectory directory : emptyIfNull(metadata.getDirectoriesOfType(GpsDirectory.class))) {
				if (!directory.containsTag(GpsDirectory.TAG_TIME_STAMP)) {
					continue;
				}
				final int[] time = directory.getIntArray(7);
				final String date = directory.getString(29);
				final Object[] values = new MessageFormat("{0,number}:{1,number}:{2,number}").parse(date);
				final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
				calendar.set(((Number) values[0]).intValue(), ((Number) values[1]).intValue() - 1, ((Number) values[2]).intValue(), time[0], time[1], time[2]);
				return calendar.getTime();
			}
		} catch (final ParseException e) {
			log.warn("Cannot read Gps-Date", e);
		}
		return null;
	}

	private static Integer readInteger(final Metadata metadata, final Class<? extends Directory> directory, final int tag) {
		for (final Directory directory2 : emptyIfNull(metadata.getDirectoriesOfType(directory))) {
			final Integer value = directory2.getInteger(tag);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static Integer readIso(final Metadata metadata) {
		return readInteger(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
	}

	private static Collection<String> readKeywords(final Metadata metadata) {
		final Collection<String> ret = new LinkedHashSet<>();
		for (final IptcDirectory iptcDirectory : emptyIfNull(metadata.getDirectoriesOfType(IptcDirectory.class))) {
			final String[] iptcKeywords = iptcDirectory.getStringArray(IptcDirectory.TAG_KEYWORDS);
			if (iptcKeywords != null) {
				ret.addAll(Arrays.asList(iptcKeywords));
			}
		}
		for (final XmpDirectory directory : emptyIfNull(metadata.getDirectoriesOfType(XmpDirectory.class))) {
			ret.addAll(readAllElements(directory, XMPConst.NS_IPTCCORE, "Keywords"));
			ret.addAll(readAllElements(directory, XMPConst.NS_DC, "subject"));
		}
		return ret;
	}

	private static Integer readRating(final Metadata metadata) {
		for (final XmpDirectory directory : emptyIfNull(metadata.getDirectoriesOfType(XmpDirectory.class))) {
			final XMPMeta meta = directory.getXMPMeta();
			try {
				return meta.getPropertyInteger(XMPConst.NS_XMP, "Rating");
			} catch (final XMPException e) {
				log.error("Cannot read rating", e);
			}
		}

		return null;
	}

	private static String readString(final Metadata metadata, final Class<? extends Directory> directory, final int tag) {
		for (final Directory dir : emptyIfNull(metadata.getDirectoriesOfType(directory))) {
			if (dir.containsTag(tag)) {
				return dir.getString(tag);
			}
		}
		return null;
	}

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

	@Override
	public void readMetadata(final ObjectLoader objectLoader, final AlbumEntryMetadataBuilder metadata) throws IOException {
		try {
			final Metadata readMetadata = ImageMetadataReader.readMetadata(objectLoader.openStream());

			final Double exposureTime = readExposureTime(readMetadata);
			if (exposureTime != null) {
				metadata.exposureTime(exposureTime);
			}
			final Double fNumber = readFNumber(readMetadata);
			if (fNumber != null) {
				metadata.fNumber(fNumber);
			}
			final Date cameraDate = readCameraDate(readMetadata);
			if (cameraDate != null) {
				metadata.captureDate(cameraDate);
			}
			final Date gpsDate = readGpsDate(readMetadata);
			if (gpsDate != null) {
				metadata.gpsDate(gpsDate);
			}
			final Double focalLength = readFocalLength(readMetadata);
			if (focalLength != null) {
				metadata.focalLength(focalLength);
			}
			final Integer iso = readIso(readMetadata);
			if (iso != null) {
				metadata.iso(iso);
			}
			final Collection<String> keywords = readKeywords(readMetadata);
			if (keywords != null && !keywords.isEmpty()) {
				metadata.keywords(keywords);
			}
			final Integer rating = readRating(readMetadata);
			if (rating != null) {
				metadata.rating(rating);
			}
			final String cameraMake = readCameraMake(readMetadata);
			if (cameraMake != null) {
				metadata.cameraMake(cameraMake);
			}
			final String cameraModel = readCameraModel(readMetadata);
			if (cameraModel != null) {
				metadata.cameraModel(cameraModel);
			}
			final String caption = readCaption(readMetadata);
			if (caption != null) {
				metadata.caption(caption);
			}
			final String cameraSerial = readCameraSerial(readMetadata);
			if (cameraSerial != null) {
				metadata.cameraSerial(cameraSerial);
			}
		} catch (final ImageProcessingException e) {
			log.warn("Error reading metdata", e);
		}
	}
}
