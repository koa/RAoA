package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.bergturbenthal.raoa.server.AlbumAccess;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:spring-test/services.xml")
public class TestAlbumImport {

  @Autowired
  private AlbumAccess albumAccess;
  @Autowired
  private ExecutorService executorService;
  private final File importBaseDir = new ClassPathResource("photos/testalbum").getFile();

  public TestAlbumImport() throws IOException {
  }

  @Test
  public void testImport() throws InterruptedException {
    albumAccess.importFiles(importBaseDir);
  }

}
