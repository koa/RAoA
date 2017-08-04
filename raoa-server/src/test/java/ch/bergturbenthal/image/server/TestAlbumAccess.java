package ch.bergturbenthal.image.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import ch.bergturbenthal.image.server.configuration.TestConfiguration;
import ch.bergturbenthal.raoa.server.Album;
import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.AlbumImage;
import ch.bergturbenthal.raoa.server.thumbnails.ThumbnailSize;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class)
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
						final File thumbnail = image.getThumbnail(ThumbnailSize.SMALL);
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
