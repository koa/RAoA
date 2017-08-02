package ch.bergturbenthal.raoa.server.spring.test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.service.ReactiveAlbumAccess;
import ch.bergturbenthal.raoa.server.spring.service.impl.BareGitReactiveAlbumAccess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestReactiveRepository {

	public static void main(final String[] args) {
		final ServerConfiguration serverConfiguration = new ServerConfiguration();
		serverConfiguration.setAlbumBaseDir("/media/akoenig/Transfer HD 3TB/");
		final ObjectMapper objectMapper = new ObjectMapper();
		final ReactiveAlbumAccess albumAccess = new BareGitReactiveAlbumAccess(serverConfiguration, objectMapper);
		log.info("Start walk");
		albumAccess.listAlbums().subscribe(s -> {
		}, e -> log.warn("Error: ", e));
		log.info("End walk");
	}
}
