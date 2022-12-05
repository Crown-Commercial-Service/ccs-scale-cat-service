package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStoreFactory;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncExecutor;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.RetryableException;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.EventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.SupplierService;

import java.util.List;

@RequiredArgsConstructor
@Component("JaggaerSupplierPush")
@Slf4j
public class JaggaerSupplierPush implements AsyncConsumer<JaggaerSupplierEventData> {
    private final EventService eventService;
    private final RetryableTendersDBDelegate dbDelegate;
    private final SupplierService supplierService;
    private final SupplierStoreFactory factory;

    @Override
    public void accept(String principal, JaggaerSupplierEventData data) {
        var event = getEvent(data);
        ProcurementProject project = event.getProject(); //dbDelegate.findProcurementProjectById(data.getProjectId()).orElseThrow();
        ProcurementEvent existingEvent = null;
        List<Supplier> suppliers = null;


        if (null != data.getExistingEventId()) {
            existingEvent = dbDelegate.findProcurementEventById(data.getExistingEventId()).orElse(null);
            if (null != existingEvent) {
                suppliers = eventService.getSuppliers(project, existingEvent, data.getEventType(), data.getTwoStageEvent());
            }
        }

        if (null == suppliers) {
            suppliers = supplierService.resolveSuppliers(project.getCaNumber(), project.getLotNumber());
        }

        if(null != suppliers) {
            SupplierStore store = factory.getStore(event);
            store.storeSuppliers(event, suppliers, data.getOverWrite(), principal);
            log.info("Successfully pushed {} suppliers to project {}, event {}", suppliers.size(), project.getId(), event.getEventID());
        }else{
            log.info("No suppliers are synced to project");
        }
    }


    @SneakyThrows
    private ProcurementEvent getEvent(JaggaerSupplierEventData data) {
        var event = dbDelegate.findProcurementEventById(data.getEventId()).orElse(null);
        if (null != event)
            return event;

        int i = 0;
        while (null == event && i < 6) {
            Thread.sleep(1000);
            i++;
            event = dbDelegate.findProcurementEventById(data.getEventId()).orElse(null);
        }
        return event;
    }

    @Override
    public void onError(String errorCode, Throwable cause) {

    }

    @Override
    public boolean canRetry(String errorCode, RetryableException re) {
        return false;
    }

    @Override
    public String getTaskName() {
        return "JaggaerSupplierPush";
    }
}
