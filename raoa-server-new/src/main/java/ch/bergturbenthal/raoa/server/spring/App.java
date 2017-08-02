package ch.bergturbenthal.raoa.server.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.boot.autoconfigure.security.oauth2.resource.AuthoritiesExtractor;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
@EnableOAuth2Sso
@EnableEurekaClient
public class App {

	// @Bean
	// public class SecurityConfig extends WebSecurityConfigurerAdapter {
	//
	// @Override
	// protected void configure(final HttpSecurity http) throws Exception {
	// http.csrf().disable();
	// }
	// }

	public static void main(final String[] args) throws Exception {
		SpringApplication.run(App.class, args);
	}

	@Bean
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2).select().apis(RequestHandlerSelectors.any()).paths(PathSelectors.any()).build();
	}

	@Bean
	public AuthoritiesExtractor authoritiesExtractor() {
		return new AuthoritiesExtractor() {

			@Override
			public List<GrantedAuthority> extractAuthorities(final Map<String, Object> map) {
				final List<GrantedAuthority> ret = new ArrayList<>();
				final String domain = (String) map.get("hd");
				if (domain != null && domain.equals("teamkoenig.ch")) {
					ret.add(new SimpleGrantedAuthority("USER"));
				}
				final Boolean emailOk = (Boolean) map.get("email_verified");
				if (emailOk != null && emailOk.booleanValue()) {
					final Object email = map.get("email");
					if (email != null && email.equals("andreas@teamkoenig.ch")) {
						ret.add(new SimpleGrantedAuthority("ADMIN"));
					}
				}
				return ret;
			}
		};
	}

}
