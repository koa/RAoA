package ch.bergturbenthal.raoa.server.spring.configuration;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2SsoDefaultConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2SsoProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@Order(100)
@EnableWebSecurity
public class SecurityConfiguration extends OAuth2SsoDefaultConfiguration {

	public SecurityConfiguration(final ApplicationContext applicationContext, final OAuth2SsoProperties sso) {
		super(applicationContext, sso);
	}

	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		super.configure(http);
		http.antMatcher("/**").authorizeRequests().anyRequest().permitAll();
		http.csrf().disable();
	}

	@Override
	public int getOrder() {
		return super.getOrder() - 1;
	}

}
