package uk.gov.crowncommercial.dts.scale.cat.model.documentupload;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class DocumentFile {

  String url;

}
