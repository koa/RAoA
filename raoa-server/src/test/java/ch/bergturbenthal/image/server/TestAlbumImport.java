package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

import ch.bergturbenthal.image.server.configuration.TestConfiguration;
import ch.bergturbenthal.raoa.server.AlbumAccess;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class)
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
