package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class DataTemplate {
    Integer id;
    String templateName;
    Integer parent;
    Boolean mandatory;
    List<TemplateCriteria> criteria;
}
