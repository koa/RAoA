package ch.bergturbenthal.raoa.server.spring.service;

import reactor.core.publisher.Flux;

public interface ReactiveAlbumAccess {
	Flux<String> listAlbums();

}
