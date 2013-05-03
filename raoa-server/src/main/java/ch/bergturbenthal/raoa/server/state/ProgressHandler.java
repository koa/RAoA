package ch.bergturbenthal.raoa.server.state;

import java.io.Closeable;

public interface ProgressHandler extends Closeable {
	@Override
	void close();

	Closeable notfiyProgress(final String stateDescription);

	void finishProgress();
}
