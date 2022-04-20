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

  public Resource getResource(final String url) {
    if (url.startsWith("classpath:")) {
      return new ClassPathResource(url);
    }
    try {
      return new UrlResource(url);
    } catch (MalformedURLException e) {
      throw new TendersDBDataException(e.getMessage());
    }
  }

}
