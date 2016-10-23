package ch.bergturbenthal.raoa.server.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.controller.MainController;
import ch.bergturbenthal.raoa.server.spring.service.impl.BareGitAlbumAccess;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableAutoConfiguration
@ComponentScan(basePackageClasses = { ServerConfiguration.class, BareGitAlbumAccess.class, MainController.class })
@EnableScheduling
@EnableSwagger2
@Configuration
public class App {
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(App.class, args);
	}

	@Bean
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2).select().apis(RequestHandlerSelectors.any()).paths(PathSelectors.any()).build();
	}
}
