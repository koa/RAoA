package ch.bergturbenthal.image.server;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/spring/services.xml")
public class TestAlbumAccess {
  @Autowired
  private FileAlbumAccess albumAccess;

  @Test
  public void testFindAlbums() {
    for (final Album album : albumAccess.listAlbums()) {
      for (final AlbumImage image : album.listImages()) {
        final File thumbnail = image.getThumbnail(400, 400, false);
        System.out.println(image + ":" + thumbnail.length());
      }
      System.out.println("- " + album + ":" + album.totalSize());
    }
  }
}
