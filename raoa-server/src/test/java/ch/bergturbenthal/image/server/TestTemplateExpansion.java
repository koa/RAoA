package ch.bergturbenthal.image.server;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.util.UriTemplate;

public class TestTemplateExpansion {
  @Test
  public void testEasyCase() {
    final UriTemplate template = new UriTemplate("http://localhost/albums/{albums}/entry/{entry}/thumbnail");
    final URI expanded = template.expand("album1", "entry1");
    Assert.assertEquals("http://localhost/albums/album1/entry/entry1/thumbnail", expanded.toString());
  }

  @Test
  public void testWithSpace() {
    final UriTemplate template = new UriTemplate("http://localhost/albums/{albums}/entry/{entry}/thumbnail");
    final URI expanded = template.expand("album 1", "entry 1");
    Assert.assertEquals("http://localhost/albums/album%201/entry/entry%201/thumbnail", expanded.toString());
  }
}
