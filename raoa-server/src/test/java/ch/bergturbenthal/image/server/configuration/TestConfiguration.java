package ch.bergturbenthal.image.server.configuration;

import java.io.File;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import ch.bergturbenthal.raoa.server.AlbumAccess;
import ch.bergturbenthal.raoa.server.FileAlbumAccess;
import ch.bergturbenthal.raoa.server.configuration.ServerConfiguration;

@Configuration
@Import(ServerConfiguration.class)
public class TestConfiguration {
	@Bean
	public AlbumAccess fileAlbumAccess() {
		final FileAlbumAccess fileAlbumAccess = new FileAlbumAccess();
		fileAlbumAccess.setBaseDir(new File("repository").getAbsoluteFile());
		fileAlbumAccess.setImportBaseDir(new File("target/test-classes/photos").getAbsoluteFile());
		fileAlbumAccess.setInstanceName("unit-test-server");
		return fileAlbumAccess;
	}

}
