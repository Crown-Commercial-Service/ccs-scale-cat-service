package uk.gov.crowncommercial.dts.scale.cat.model;

import org.springframework.http.MediaType;
import lombok.Value;

@Value
public class DocumentAttachment {

  byte[] data;
  MediaType contentType;
}
