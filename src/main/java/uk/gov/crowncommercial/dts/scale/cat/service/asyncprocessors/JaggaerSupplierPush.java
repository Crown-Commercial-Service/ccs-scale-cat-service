package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStoreFactory;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor.TaskUtils;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.EventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.SupplierService;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

@RequiredArgsConstructor
@Component("JaggaerSupplierPush")
@Slf4j
public class JaggaerSupplierPush implements AsyncConsumer<JaggaerSupplierEventData> {
    private final EventService eventService;
    private final RetryableTendersDBDelegate dbDelegate;
    private final SupplierService supplierService;
    private final SupplierStoreFactory factory;

    @Override
    @Transactional
    public String accept(String principal, JaggaerSupplierEventData data) {
        var event = getEvent(data);
        ProcurementProject project = event.getProject(); //dbDelegate.findProcurementProjectById(data.getProjectId()).orElseThrow();
        ProcurementEvent existingEvent = null;
        List<Supplier> suppliers = data.getSuppliers();


        if (null == suppliers && null != data.getExistingEventId()) {
            existingEvent = dbDelegate.findProcurementEventById(data.getExistingEventId()).orElse(null);
            if (null != existingEvent) {
                suppliers = eventService.getSuppliers(project, existingEvent, data.getEventType(), data.getTwoStageEvent());
            }
        }

        if (null == suppliers) {
            suppliers = supplierService.resolveSuppliers(project.getCaNumber(), project.getLotNumber());
        }

        if(null != suppliers) {
            Map<String, String> options = Map.ofEntries(entry("store", "jaggaer"));
            SupplierStore store = factory.getStore(event);
            try {
                store.storeSuppliers(event, suppliers, data.getOverWrite(), principal, options);
            }catch(JaggaerApplicationException jae){
                if(jae.getMessage().contains("Code: [-998]")){
                    throw new RetryableException("-998", jae.getMessage(), jae);
                }else if(jae.getMessage().contains("Code: [-999]")){
                    throw new RetryableException("-999", jae.getMessage(), jae);
                }else
                    throw jae;
            }
            log.info("Successfully pushed {} suppliers to project {}, event {}", suppliers.size(), project.getId(), event.getEventID());
            return "Pushed " + suppliers.size() + " suppliers to Jaggaer";
        }else{
            log.info("No suppliers are synced to project");
            return "No suppliers available for Jaggaer push";
        }
    }

    @Override
    public List<ErrorHandler> getErrorHandlers() {
        return Arrays.asList(JaggaerErrorHandler.INSTANCE, NoopErrorHandler.INSTANCE);
    }


    @SneakyThrows
    private ProcurementEvent getEvent(JaggaerSupplierEventData data) {
        return TaskUtils.get(()-> dbDelegate.findProcurementEventById(data.getEventId()).orElse(null), 6);
    }
//
//    @Override
//    public boolean canRetry(String errorCode, RetryableException re) {
//        return re.getErrorCode().equals("-998");
//    }

    @Override
    public String getIdentifier(JaggaerSupplierEventData data) {
        return data.getEventType() + ":" + data.getProjectId() + "/" + data.getEventId();
    }

    @Override
    public String getTaskName() {
        return "JaggaerSupplierPush";
    }
}
