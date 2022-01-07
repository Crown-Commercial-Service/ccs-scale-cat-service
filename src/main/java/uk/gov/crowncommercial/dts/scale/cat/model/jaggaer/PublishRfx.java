package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.Builder;
import lombok.Value;

/**
 *
 */
@Value
@Builder
public class PublishRfx {

  String rfxId;
  String rfxReferenceCode;
  OwnerUser operatorUser;

  @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
  Instant newClosingDate;

}
