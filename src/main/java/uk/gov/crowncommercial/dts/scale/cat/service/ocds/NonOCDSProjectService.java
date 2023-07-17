package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.NonOCDSRelease;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.NonOCDSTender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackage;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class NonOCDSProjectService {
    public void populateCancelled(ProjectPackage pp, ProcurementProject procurementProject) {
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(procurementProject);
        if (null != pe.getCloseDate() && "cancelled".equalsIgnoreCase(pe.getTenderStatus())) {
            NonOCDSRelease release = new NonOCDSRelease()
                    .tender(new NonOCDSTender()
                            .cancellationDate(OffsetDateTime.ofInstant(pe.getCloseDate(), ZoneId.systemDefault())));
            pp.setNonOCDS(release);
        }
    }
}
