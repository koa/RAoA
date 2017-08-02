package ch.bergturbenthal.raoa.server.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import ch.bergturbenthal.raoa.server.configuration.ServerConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@Import(ServerConfiguration.class)
public class Starter {
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(Starter.class, args);
	}

}
