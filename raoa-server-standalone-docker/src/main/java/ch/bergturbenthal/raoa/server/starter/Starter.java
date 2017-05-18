package ch.bergturbenthal.raoa.server.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.bergturbenthal.raoa.server.controller.PingController;
import ch.bergturbenthal.raoa.server.watcher.DirectoryNotificationService;

@SpringBootApplication
@ImportResource("classpath:spring/services.xml")
// @EnableEurekaClient
@EnableScheduling
@ComponentScan(basePackageClasses = { PingController.class, DirectoryNotificationService.class })
public class Starter {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		SpringApplication.run(Starter.class, args);
	}

}
