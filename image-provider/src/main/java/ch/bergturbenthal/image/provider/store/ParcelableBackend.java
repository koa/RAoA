package ch.bergturbenthal.image.provider.store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.os.Parcel;
import android.os.Parcelable;
import ch.bergturbenthal.image.provider.util.IOUtil;

public class ParcelableBackend<T extends Parcelable> extends AbstractFileBackend<T> {

  private static final String SUFFIX = ".parc";

  private final Class<T> type;

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
