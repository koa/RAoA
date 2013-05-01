package ch.bergturbenthal.image.provider.store;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.provider.util.IOUtil;

public class ParcelableBackend<T extends Parcelable> extends AbstractFileBackend<T> {

	private static String PARCELABLE_VERSION = "parcelable.version";

	private static final String SUFFIX = ".parc";
	private final Class<T> type;

	public static void checkVersion(final File basePath, final long version) {
		boolean versionOk = false;
		final File versionFile = new File(basePath, PARCELABLE_VERSION);
		if (versionFile.exists()) {
			try {
				final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(versionFile), "utf-8"));
				try {
					final String versionString = reader.readLine();
					if (versionString != null) {
						versionOk = Long.parseLong(versionString) == version;
					}
				} finally {
					reader.close();
				}
			} catch (final IOException e) {
				// cannot read File
				versionOk = false;
			} catch (final NumberFormatException e) {
				// no readable content
				versionOk = false;
			}
		}
		if (!versionOk) {
			if (!basePath.exists()) {
				basePath.mkdirs();
			}
			cleanParcFiles(basePath);
			try {
				final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(versionFile), "utf-8"));
				try {
					writer.println(version);
				} finally {
					writer.close();
				}
			} catch (final IOException e) {
				throw new RuntimeException("Cannot write version file " + versionFile, e);
			}
		}
	}

	private static void cleanParcFiles(final File basePath) {
		for (final File file : basePath.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				return pathname.isDirectory() || pathname.getName().endsWith(SUFFIX);
			}
		})) {
			if (file.isDirectory()) {
				cleanParcFiles(file);
			} else {
				file.delete();
			}
		}
	}

	public ParcelableBackend(final File basePath, final Class<T> type) {
		super(basePath, SUFFIX, new FileSerializer<T>() {

			@Override
			public T readFromFile(final File f) throws IOException {
				final GZIPInputStream is = new GZIPInputStream(new FileInputStream(f));
				final T readValue;
				try {
					final Parcel parcel = Parcel.obtain();
					final byte[] data = IOUtil.readStream(is);
					parcel.setDataPosition(0);
					parcel.unmarshall(data, 0, data.length);
					parcel.setDataPosition(0);
					readValue = parcel.readParcelable(type.getClassLoader());
					parcel.recycle();
				} finally {
					is.close();
				}
				return readValue;
			}

			@Override
			public void writeToFile(final File f, final T value) throws IOException {
				final Parcel parcel = Parcel.obtain();
				parcel.setDataPosition(0);
				parcel.writeParcelable(value, 0);
				parcel.setDataPosition(0);
				final OutputStream outputStream = new GZIPOutputStream(new FileOutputStream(f));
				try {
					outputStream.write(parcel.marshall());
				} finally {
					outputStream.close();
				}
				parcel.recycle();
			}
		});
		this.type = type;
	}

	@Override
	public Class<T> getType() {
		return type;
	}
}
