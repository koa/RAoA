package ch.bergturbenthal.raoa.server.spring.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "raoa")
public class ServerConfiguration {
	private String albumBaseDir;
	private String serverName;
}
