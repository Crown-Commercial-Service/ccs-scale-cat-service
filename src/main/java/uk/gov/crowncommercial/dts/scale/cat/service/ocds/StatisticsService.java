package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.LastRound;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsService {
    private final JaggaerService jaggaerService;
    public MapperResponse populate(Record1 record, ProjectQuery query) {
        log.debug("populating basic details");
        Release release = OcdsHelper.getRelease(record);
        ProcurementProject pp = query.getProject();
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            ExportRfxResponse rfxResponse = jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(pe.getExternalEventId());
            LastRound lastRound = rfxResponse.getSupplierResponseCounters().getLastRound();
            Bids1 bids = OcdsHelper.getBids(record);
            populateStatistics(lastRound, bids);
        });
        return new MapperResponse(record, cf);
    }

    private void populateStatistics(LastRound lastRound, Bids1 bids) {
        bids.addStatisticsItem(createStatics("suppliersInvited", lastRound.getNumSupplInvited()));
        bids.addStatisticsItem(createStatics("suppliersNotResponded", lastRound.getNumSupplNotResponded()));
        bids.addStatisticsItem(createStatics("suppliersResponded", lastRound.getNumSupplResponded()));
        bids.addStatisticsItem(createStatics("suppliersRespDeclined", lastRound.getNumSupplRespDeclined()));
        bids.addStatisticsItem(createStatics("suppliersRespExcluded", lastRound.getNumSupplRespExcluded()));
        bids.addStatisticsItem(createStatics("suppliersRespAccepted", lastRound.getNumSupplRespAccepted()));
    }

    private BidsStatistic1 createStatics(String name, Integer numSupplInvited) {
        BidsStatistic1 bs = new BidsStatistic1();
        bs.setMeasure(name);
        bs.setValue(BigDecimal.valueOf(numSupplInvited));
        return bs;
    }
}
