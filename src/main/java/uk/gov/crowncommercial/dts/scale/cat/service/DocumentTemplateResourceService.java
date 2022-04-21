package uk.gov.crowncommercial.dts.scale.cat.service;

import java.net.MalformedURLException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;

/**
 * Document template source service. In the future, the proforma templates should be sourced from a
 * central location.
 */
@Service
public class DocumentTemplateResourceService {

  private static final String CLASSPATH_URL_PREFIX = "classpath:";

  public Resource getResource(final String url) {
    if (url.startsWith(CLASSPATH_URL_PREFIX)) {
      return new ClassPathResource(url.substring(CLASSPATH_URL_PREFIX.length()));
    }
    try {
      return new UrlResource(url);
    } catch (MalformedURLException e) {
      throw new TendersDBDataException(e.getMessage());
    }
  }

}
