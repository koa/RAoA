package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import ch.bergturbenthal.raoa.server.spring.configuration.ServerConfiguration;
import ch.bergturbenthal.raoa.server.spring.model.AlbumMetadata;
import ch.bergturbenthal.raoa.server.spring.model.event.Event;
import ch.bergturbenthal.raoa.server.spring.model.event.NewRepositoryEvent;
import ch.bergturbenthal.raoa.server.spring.model.event.RemovedRepositoryEvent;
import ch.bergturbenthal.raoa.server.spring.model.event.UpdatedRepositoryEvent;
import ch.bergturbenthal.raoa.server.spring.service.ReactiveAlbumAccess;
import ch.bergturbenthal.raoa.server.spring.util.FindGitDirWalker;
import lombok.Cleanup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class BareGitReactiveAlbumAccess implements ReactiveAlbumAccess {
	private static final String _REFS_HEADS = "refs/heads/";
	private static final String MASTER_REF = _REFS_HEADS + "master";

	private static final String RAOA_JSON = ".raoa.json";

	private final ObjectReader albumMetadataReader;
	private final ObjectWriter albumMetadataWriter;
	private final Map<String, String> currentState = new ConcurrentHashMap<>();
	private final LoadingCache<Repository, Mono<AlbumMetadata>> metadataOfRepository = CacheBuilder	.newBuilder()
																																																	.expireAfterWrite(20, TimeUnit.SECONDS)
																																																	.build(CacheLoader.from(repo -> readAlbumMetadataFromRepository(repo).cache()));
	private final LoadingCache<File, Flux<File>> repoGitDirs = CacheBuilder	.newBuilder()
																																					.expireAfterWrite(20, TimeUnit.SECONDS)
																																					.build(CacheLoader.from(file -> new FindGitDirWalker().findGitDirs(file).cache()));
	private final LoadingCache<File, Repository> repositoryOfFile = CacheBuilder.newBuilder()
																																							.expireAfterWrite(20, TimeUnit.SECONDS)
																																							.build(CacheLoader.from(this::createRepositoryFromDirectory));
	private final ServerConfiguration serverConfiguration;

	@Autowired
	public BareGitReactiveAlbumAccess(final ServerConfiguration serverConfiguration, final ObjectMapper objectMapper) {
		this.serverConfiguration = serverConfiguration;
		albumMetadataReader = objectMapper.readerFor(AlbumMetadata.class);

		albumMetadataWriter = objectMapper.writerFor(AlbumMetadata.class);
		updateCurrentState();
	}

	private Flux<File> availableRepositories() {
		return repoGitDirs.getUnchecked(new File(serverConfiguration.getAlbumBaseDir()));
	}

	private Repository createRepositoryFromDirectory(final File file) {
		try {
			return Git.init().setBare(true).setDirectory(file).call().getRepository();
		} catch (IllegalStateException | GitAPIException e) {
			throw new RuntimeException("Cannot access repository on " + file, e);
		}
	}

	private Flux<Repository> findAllRepositories() {
		return availableRepositories().map(repositoryOfFile::getUnchecked);
	}

	private Flux<Tuple2<String, ObjectId>> findFiles(final Repository repository, final Mono<ObjectId> foundRef, final Collection<String> filenames) {
		return foundRef.flatMapMany(ref -> Flux.create(sink -> {
			try {
				@Cleanup
				final RevWalk revWalk = new RevWalk(repository);
				@Cleanup
				final TreeWalk treeWalk = new TreeWalk(repository);

				final RevCommit commit = revWalk.parseCommit(ref);
				final RevTree tree = commit.getTree();
				treeWalk.addTree(tree);
				final TreeFilter treeFilter = PathFilterGroup.createFromStrings(filenames);
				treeWalk.setRecursive(treeFilter.shouldBeRecursive());
				treeWalk.setFilter(treeFilter);
				while (treeWalk.next()) {
					if (treeWalk.isSubtree()) {
						continue;
					}
					final String pathString = treeWalk.getPathString();
					final ObjectId objectId = treeWalk.getObjectId(0);
					sink.next(Tuples.of(pathString, objectId));
				}
				sink.complete();
			} catch (RevisionSyntaxException | IOException e) {
				sink.error(e);
			}
		}));
	}

	private Flux<Tuple2<String, ObjectId>> findFiles(final Repository repository, final String refName, final Collection<String> filenames) {
		return findFiles(repository, readRef(repository, refName), filenames);
	}

	private Mono<ObjectId> findSingleFile(final Repository repo, final String refName, final String fileName) {
		return findFiles(repo, refName, Collections.singleton(fileName)).singleOrEmpty().map(t -> t.getT2());
	}

	@Override
	public Flux<String> listAlbums() {
		return findAllRepositories().flatMap(metadataOfRepository::getUnchecked).filter(t -> t.getAlbumId() != null).map(t -> t.getAlbumId()).cache();
	}

	private AlbumMetadata loadMetadataFromRepository(final Repository repo, final AnyObjectId objectid) {
		try {
			@Cleanup
			final ObjectStream beforeStream = repo.open(objectid).openStream();
			return albumMetadataReader.readValue(beforeStream);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot read metdata from " + repo, e);
		}
	}

	private Mono<AlbumMetadata> readAlbumMetadataFromRepository(final Repository repo) {
		return findSingleFile(repo, MASTER_REF, RAOA_JSON).map(objectid -> loadMetadataFromRepository(repo, objectid));

	}

	private Mono<ObjectId> readRef(final Repository repo, final String refName) {
		try {
			final ObjectId ref = repo.resolve(refName);
			if (ref != null) {
				return Mono.just(ref);
			}
			return Mono.empty();
		} catch (RevisionSyntaxException | IOException e) {
			return Mono.error(e);
		}
	}

	private void sendEvents(final Event event) {

	}

	public void updateCurrentState() {
		final Flux<Tuple2<String, String>> newTuples = findAllRepositories().flatMap(t -> Mono.when(readRef(t, MASTER_REF), metadataOfRepository.getUnchecked(t)))
																																				.filter(v -> v.getT2().getAlbumId() != null)
																																				.map(t -> Tuples.of(t.getT2().getAlbumId(), t.getT1().getName()));
		final Set<String> remainingAlbums = Collections.synchronizedSet(new HashSet<>(currentState.keySet()));
		newTuples.subscribe(entry -> {
			final String albumId = entry.getT1();
			remainingAlbums.remove(albumId);
			final String version = entry.getT2();
			currentState.compute(albumId, (key, oldValue) -> {
				if (oldValue == null) {
					sendEvents(new NewRepositoryEvent(version, albumId, Instant.now()));
				} else if (!version.equals(oldValue)) {
					sendEvents(new UpdatedRepositoryEvent(version, albumId, Instant.now(), oldValue));
				}
				return version;
			});
		});
		for (final String removedAlbumId : remainingAlbums) {
			sendEvents(new RemovedRepositoryEvent(removedAlbumId, Instant.now()));
		}
	}

}
