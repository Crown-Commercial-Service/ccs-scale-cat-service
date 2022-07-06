package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchRfxsDataList {

  Set<ExportRfxResponse> rfx;
}
