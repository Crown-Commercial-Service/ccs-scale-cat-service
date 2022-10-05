package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class DataTemplate {
    @JsonInclude(Include.NON_NULL)
    Integer id;
    @JsonInclude(Include.NON_NULL)
    String templateName;
    @JsonInclude(Include.NON_NULL)
    Integer parent;
    @JsonInclude(Include.NON_NULL)
    Boolean mandatory;

    List<TemplateCriteria> criteria;
}
