package ch.bergturbenthal.raoa.server;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * (c) 2012 panter llc, Zurich, Switzerland.
 */

/**
 * Resolves repositories for daemon
 * 
 * @param <C>
 *          type of connection.
 * 
 */
public class AlbumRepositoryResolver<C> implements RepositoryResolver<C> {

  @Autowired
  private AlbumAccess albumAccess;

  @Override
  public Repository open(final C req, final String name) throws RepositoryNotFoundException, ServiceNotAuthorizedException,
                                                        ServiceNotEnabledException {
    if (".meta".equals(name))
      return albumAccess.getMetaRepository();
    final Album album = albumAccess.getAlbum(name);
    if (album == null)
      throw new RepositoryNotFoundException(name);
    return album.getRepository();
  }

}
