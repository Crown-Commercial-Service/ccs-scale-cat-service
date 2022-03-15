package uk.gov.crowncommercial.dts.scale.cat.model.documentupload;

import java.time.ZonedDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a document and its (virus check) state on the external Document Upload Service
 */
@Value
@Builder
@Jacksonized
public class DocumentStatus {

  String id;

  @JsonProperty("createdAt")
  ZonedDateTime createdAt;
  String state;

  @JsonProperty("documentFile")
  DocumentFile documentFile;

}
