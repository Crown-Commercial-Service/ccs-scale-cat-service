package uk.gov.crowncommercial.dts.scale.cat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementStageEvent;

/**
 * MapStruct mapping definition for converting ConfiguredEventModel objects to UsableEventViewModel and PostPublishEventViewModel objects
 */
@Mapper(componentModel = "spring")
public interface ProcurementEventMapper {

    @Mapping(target = "procurementTemplatePayload", expression = "java(getThisProcurementTemplatePayload(source))")
    public abstract ProcurementEvent procurementStageEventToProcurementEvent(ProcurementStageEvent source);

    @Mapping(target = "procurementTemplatePayload", expression = "java(getThisProcurementTemplatePayload(source))")
    public abstract ProcurementStageEvent procurementEventToProcurementStageEvent(ProcurementEvent source);

    default String getThisProcurementTemplatePayload(final ProcurementEvent source) {
        return source.getThisProcurementTemplatePayload();
    }

    default String getThisProcurementTemplatePayload(final ProcurementStageEvent source) {
        return source.getThisProcurementTemplatePayload();
    }

    default void setProcurementTemplatePayload(final ProcurementEvent event, final String source) {
        event.setThisProcurementTemplatePayload(source);
    }

    default void setProcurementTemplatePayload(final ProcurementStageEvent event, final String source) {
        event.setThisProcurementTemplatePayload(source);
    }
}
