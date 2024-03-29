package uk.gov.crowncommercial.dts.scale.cat.model;

import org.springframework.http.MediaType;
import lombok.Builder;
import lombok.Value;

import java.io.InputStream;

@Value
@Builder
public class DocumentAttachment {

  byte[] data;
  MediaType contentType;
  String fileName;
  InputStream streamData;
}
