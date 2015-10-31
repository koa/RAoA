package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.util.Base64;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.HashGenerator;

@Service
public class HashGeneratorImpl implements HashGenerator {

	@Override
	@Cacheable
	public String generateHash(final String value) {
		return Base64.getEncoder().encodeToString(DigestUtils.md5(value));
	}

}
