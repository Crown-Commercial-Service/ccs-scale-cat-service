package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractOcdsService {
    private JaggaerService jaggaerService;
    @SneakyThrows
    public ExportRfxResponse getLatestRFXWithSuppliers(ProjectQuery pq){
        if(null != pq.getLatestEventRFXWithSuppliers())
            return pq.getLatestEventRFXWithSuppliers().get();

        ProcurementEvent pe = EventsHelper.getAwardEvent(pq.getProject());

        CompletableFuture<ExportRfxResponse> cf = CompletableFuture.supplyAsync(()-> {
            return jaggaerService.getRfxWithSuppliers(pe.getExternalEventId());
        });
        ((ProjectRequest)pq).setLatestEventRFXWithSuppliers(cf);
        return cf.get();
    }

    @SneakyThrows
    public ExportRfxResponse getFirstRFXWithSuppliers(ProjectQuery pq){
        if(null != pq.getFirstEventRFXWithSuppliers())
            return pq.getFirstEventRFXWithSuppliers().get();

        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pq.getProject());

        CompletableFuture<ExportRfxResponse> cf = CompletableFuture.supplyAsync(()-> {
            return jaggaerService.getRfxWithSuppliers(pe.getExternalEventId());
        });
        ((ProjectRequest)pq).setFirstEventRFXWithSuppliers(cf);
        return cf.get();
    }

    @Autowired
    public void setJaggaerService(JaggaerService service){
        this.jaggaerService = service;
    }
}
