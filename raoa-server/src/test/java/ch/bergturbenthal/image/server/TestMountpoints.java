package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class TestMountpoints {
  @Test
  @Ignore
  public void testMountpoints() throws IOException, InterruptedException {
    final Path path = new File("/media/akoenig").toPath();
    final FileSystem fileSystem = path.getFileSystem();
    final WatchService watchService = fileSystem.newWatchService();
    path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY,
                  StandardWatchEventKinds.OVERFLOW);

    final WatchKey watchKey = watchService.take();
    System.out.println(path);
    final List<WatchEvent<?>> events = watchKey.pollEvents();
    for (final WatchEvent<?> watchEvent : events) {
      final Object context = watchEvent.context();
      final long count = watchEvent.count();
      final Kind<?> kind = watchEvent.kind();
      System.out.println(" - " + context + ":" + count + ":" + kind);
    }
  }
}
