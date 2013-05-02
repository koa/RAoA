package ch.bergturbenthal.image.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumImage;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:spring-test/services.xml")
public class TestAlbumAccess {

  @Autowired
  private AlbumAccess albumAccess;
  @Autowired
  private ExecutorService executorService;

  @Test
  public void testFindAlbums() throws InterruptedException {
    final Collection<Callable<Void>> runnables = new ArrayList<Callable<Void>>();
    for (final Album album : albumAccess.listAlbums().values()) {
      for (final AlbumImage image : album.listImages().values()) {
        runnables.add(new Callable<Void>() {

          @Override
          public Void call() throws Exception {
            final File thumbnail = image.getThumbnail();
            System.out.println(image + ":" + thumbnail.length());
            return null;
          }
        });
      }
      System.out.println("- " + album + ":" + album.getRepositorySize());
    }
    executorService.invokeAll(runnables);
  }
}
