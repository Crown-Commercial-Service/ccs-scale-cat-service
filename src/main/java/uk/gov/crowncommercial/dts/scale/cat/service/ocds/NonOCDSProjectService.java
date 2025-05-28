package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.NonOCDSRelease;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.NonOCDSTender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackage;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A service which deals with interrogating non-OCDS elements of a project
 * TODO: Remove this service?  It seems to serve little purpose and this method could be moved elsewhere
 */
@Service
public class NonOCDSProjectService {
    /**
     * Sets release information on a given project package based on details of the first published event attached to the project
     */
    public void populateCancelled(ProjectPackage pp, ProcurementProject procurementProject) {
        // First, grab the first published event on the project, if any
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(procurementProject);

        if (pe != null && pe.getCloseDate() != null && pe.getTenderStatus() != null && pe.getTenderStatus().equalsIgnoreCase(Constants.EVENT_STATE_CANCELLED)) {
            // We have found a cancelled event attached to the project - so use it to populate release information in the package
            NonOCDSRelease release = new NonOCDSRelease()
                    .tender(new NonOCDSTender()
                            .cancellationDate(OffsetDateTime.ofInstant(pe.getCloseDate(), ZoneId.systemDefault())));

            pp.setNonOCDS(release);
        }
    }
}