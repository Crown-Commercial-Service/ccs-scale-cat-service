package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.LastRound;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Populates statistics information regarding a given project
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsService {
    private final JaggaerService jaggaerService;

    /**
     * Entry route for this mapper - populates basic info and triggers statistics population
     */
    public MapperResponse populate(Record1 record, ProjectQuery query) {
        CompletableFuture<Void> cf = null;

        if (query != null && query.getProject() != null) {
            ProcurementProject pp = query.getProject();
            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

            if (pe != null && pe.getExternalEventId() != null) {
                cf = CompletableFuture.runAsync(() -> {
                    ExportRfxResponse rfxResponse = jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(pe.getExternalEventId());

                    if (rfxResponse != null && rfxResponse.getSupplierResponseCounters() != null && rfxResponse.getSupplierResponseCounters().getLastRound() != null && record != null) {
                        LastRound lastRound = rfxResponse.getSupplierResponseCounters().getLastRound();
                        Bids1 bids = OcdsHelper.getBids(record);
                        populateStatistics(lastRound, bids);
                    }
                });
            }
        }

        return new MapperResponse(record, cf);
    }

    /**
     * Populates various statistics items from a given LastRound object
     */
    private void populateStatistics(LastRound lastRound, Bids1 bids) {
        if (bids != null && lastRound != null) {
            if (lastRound.getNumSupplInvited() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_INVITED, lastRound.getNumSupplInvited()));
            }

            if (lastRound.getNumSupplNotResponded() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_NO_RESPONSE, lastRound.getNumSupplNotResponded()));
            }

            if (lastRound.getNumSupplResponded() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_RESPONDED, lastRound.getNumSupplResponded()));
            }

            if (lastRound.getNumSupplRespDeclined() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_DECLINED, lastRound.getNumSupplRespDeclined()));
            }

            if (lastRound.getNumSupplRespExcluded() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_EXCLUDED, lastRound.getNumSupplRespExcluded()));
            }

            if (lastRound.getNumSupplRespAccepted() != null) {
                bids.addStatisticsItem(createStatics(Constants.MAPPERS_STATS_ACCEPTED, lastRound.getNumSupplRespAccepted()));
            }
        }
    }

    /**
     * Creates an individual BidStatistic1 object from a given set of values
     */
    private BidsStatistic1 createStatics(String name, Integer numSupplInvited) {
        if (name != null && numSupplInvited != null) {
            // Converting the numeric value should be fine, but fail silently if it isn't so it doesn't break the whole project
            try {
                BidsStatistic1 bs = new BidsStatistic1();
                bs.setMeasure(name);
                bs.setValue(BigDecimal.valueOf(numSupplInvited));

                return bs;
            } catch (Exception ex) {
                log.warn("Error parsing statistics numeric", ex);
            }
        }

        return null;
    }
}