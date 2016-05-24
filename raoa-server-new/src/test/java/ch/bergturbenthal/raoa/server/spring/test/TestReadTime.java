package ch.bergturbenthal.raoa.server.spring.test;

import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestReadTime {

	@Test
	public void testMillis() {
		long lastTime = 0;
		int incrementCount = 0;
		final long startTime = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			final long newTime = System.currentTimeMillis();
			if (newTime != lastTime) {
				incrementCount++;
			}
			lastTime = newTime;
		}
		final long elapsedTime = System.currentTimeMillis() - startTime;
		log.info("Millis: " + elapsedTime + ", resolution: " + incrementCount);
	}

	@Test
	public void testNanos() {
		long lastNanoTime = 0;
		int incrementCount = 0;
		final long startTime = System.currentTimeMillis();
		for (int i = 0; i < 1000000; i++) {
			final long newNanoTime = System.nanoTime();
			if (newNanoTime != lastNanoTime) {
				incrementCount++;
			}
			lastNanoTime = newNanoTime;
		}
		final long elapsedTime = System.currentTimeMillis() - startTime;
		log.info("Nanos: " + elapsedTime + ", resolution: " + incrementCount);
	}
}
