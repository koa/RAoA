package ch.bergturbenthal.raoa.server.starter;

import java.io.IOException;

import org.eclipse.jetty.util.resource.URLResource;
import org.springframework.core.io.Resource;

public class SpringResource extends URLResource {

  protected SpringResource(final Resource springResource) throws IOException {
    super(springResource.getURL(), null);
  }

}
