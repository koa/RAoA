package ch.bergturbenthal.raoa.server.state;

import java.io.Closeable;

import org.eclipse.jgit.lib.ProgressMonitor;

public interface CloseableProgressMonitor extends ProgressMonitor, Closeable {

}
