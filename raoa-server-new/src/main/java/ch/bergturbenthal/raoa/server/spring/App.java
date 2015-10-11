package ch.bergturbenthal.raoa.server.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.service.impl.BareGitAlbumAccess;

@EnableAutoConfiguration
@ComponentScan(basePackageClasses = { ServerConfiguration.class, BareGitAlbumAccess.class })
@EnableScheduling
public class App {
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(App.class, args);
	}
}
