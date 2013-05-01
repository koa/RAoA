package ch.bergturbenthal.image.provider.map;

public interface FieldReader<V> {
	Number getNumber(final V value);

	String getString(final V value);

	int getType();

	Object getValue(final V value);

	boolean isNull(final V value);
}