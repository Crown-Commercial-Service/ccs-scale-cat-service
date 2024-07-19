package uk.gov.crowncommercial.dts.scale.cat.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Serves as a central component which holds scheduled tasks that the application may run
 */
@Component
@Slf4j
public class TaskSchedulingClient {
    @Autowired
    AgreementsService agreementsService;

    @Value("${caching.agreements}")
    private String activeAgreements;

    /**
     * Hourly, generate a fresh cache of Agreement data from the Agreements Service
     */
    @Scheduled(fixedDelayString = "PT1H")
    public void refreshAgreementsServiceCache() {
        // Split out the configured agreements list, and iterate over each one for cache spool up
        List<String> agreementsList = Arrays.stream(activeAgreements.split(",")).toList();

        if (!agreementsList.isEmpty()) {
            agreementsList.forEach(agreementId -> {
                // First get (and generate a cache of) the Agreement Details
                AgreementDetail agreementModel = agreementsService.getAgreementDetails(agreementId);

                if (agreementModel != null && agreementModel.getLots() != null && !agreementModel.getLots().isEmpty()) {
                    // Now for each lot, get (and generate a cache of) the Lot Details
                    agreementModel.getLots().forEach(lotSummary -> {
                        if (!lotSummary.getNumber().equalsIgnoreCase("All")) {
                            LotDetail lotModel = agreementsService.getLotDetails(agreementId, lotSummary.getNumber());

                            if (lotModel != null) {
                                // Now fetch (and generate cache of) the event types for the lot
                                Collection<LotEventType> eventTypes = agreementsService.getLotEventTypes(agreementId, lotSummary.getNumber());

                                if (eventTypes != null && !eventTypes.isEmpty()) {
                                    // Now for each event type trigger a cache spool up of its data templates
                                    eventTypes.forEach(eventType -> {
                                        agreementsService.getLotEventTypeDataTemplates(agreementId, lotSummary.getNumber(), ViewEventType.fromValue(eventType.getType()));
                                    });
                                }

                                // Finally, trigger a cache spool up of the suppliers for the lot
                                agreementsService.getLotSuppliers(agreementId, lotSummary.getNumber());
                            }
                        }
                    });
                }
            });
        }
    }
}