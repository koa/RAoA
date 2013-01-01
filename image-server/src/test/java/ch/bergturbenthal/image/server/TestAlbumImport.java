package ch.bergturbenthal.image.server;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.bergturbenthal.image.server.state.StateManager;
import ch.bergturbenthal.image.server.state.StateManagerImpl;
import ch.bergturbenthal.image.server.util.RepositoryService;
import ch.bergturbenthal.image.server.util.RepositoryServiceImpl;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class TestAlbumImport {
  @Configuration
  static class Config {
    private static ClassPathResource resource = new ClassPathResource("photos/testalbum");
    private static File albumBaseDir = new File("target/testalbum", UUID.randomUUID().toString());
    static {
      try {
        // final File dir = resource.getFile();
        final File albumDir = new File(albumBaseDir, "album");
        Git.init().setDirectory(albumDir).call();
        final PrintWriter writer = new PrintWriter(new File(albumDir, ".autoadd"), "utf-8");
        writer.println("2001-01-01");
        writer.close();

      } catch (final IOException e) {
        throw new RuntimeException("Cannot initialize Test", e);
      } catch (final GitAPIException e) {
        throw new RuntimeException("Cannot initialize Test", e);
      }
    }

    @Bean
    public AlbumAccess albumAccess() throws IOException {
      final FileAlbumAccess fileAlbumAccess = new FileAlbumAccess();
      fileAlbumAccess.setBaseDir(albumBaseDir);
      fileAlbumAccess.setImportBaseDir(resource.getFile());
      return fileAlbumAccess;
    }

    @Bean
    public ScheduledExecutorService executorService() {
      return Executors.newScheduledThreadPool(4);
    }

    @Bean
    public File importBaseDir() throws IOException {
      return resource.getFile();
    }

    @Bean
    public RepositoryService repositoryService() {
      return new RepositoryServiceImpl();
    }

    @Bean
    public StateManager stateManager() {
      return new StateManagerImpl();
    }
  }

  @Autowired
  private AlbumAccess albumAccess;
  @Autowired
  private ExecutorService executorService;
  @Autowired
  private File importBaseDir;

  @Test
  public void testImport() throws InterruptedException {
    albumAccess.importFiles(importBaseDir);
  }

}
