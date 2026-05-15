package uk.gov.crowncommercial.dts.scale.cat.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mapstruct.factory.Mappers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import uk.gov.crowncommercial.dts.scale.cat.mapper.ProcurementEventMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementStageEvent;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ProcurementEventMapper.class })
public class MapStructMappingTests {
    private final ProcurementEventMapper procurementEventMapper = Mappers.getMapper(ProcurementEventMapper.class);

    @Test
    public void shouldMapFromProcurementEventToProcurementStageEvent() {
        final ProcurementEvent procurementEvent = new ProcurementEvent();
        procurementEvent.setThisProcurementTemplatePayload("my json");

        final ProcurementStageEvent procurementStageEvent = procurementEventMapper.procurementEventToProcurementStageEvent(procurementEvent);
        assertEquals("my json", procurementStageEvent.getThisProcurementTemplatePayload());
    }

    @Test
    public void shouldMapFromProcurementStageEventToProcurementEvent() {
        final ProcurementStageEvent procurementStageEvent = new ProcurementStageEvent();
        procurementStageEvent.setThisProcurementTemplatePayload("my json");

        final ProcurementEvent procurementEvent = procurementEventMapper.procurementStageEventToProcurementEvent(procurementStageEvent);
        assertEquals("my json", procurementEvent.getThisProcurementTemplatePayload());
    }
}
