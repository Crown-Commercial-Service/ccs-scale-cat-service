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
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.RetryableException;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.EventService;
import uk.gov.crowncommercial.dts.scale.cat.service.SupplierService;

import jakarta.transaction.Transactional;
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
        System.out.println("Starting accept() method with principal: " + principal);
        System.out.println("Input data: " + data);
        
        var event = getEvent(data);
        System.out.println("Retrieved event: " + event);
        
        ProcurementProject project = event.getProject();
        System.out.println("Project details - ID: " + project.getId() + ", CA Number: " + project.getCaNumber() + ", Lot Number: " + project.getLotNumber());
        
        ProcurementEvent existingEvent = null;
        List<Supplier> suppliers = data.getSuppliers();
        System.out.println("Initial suppliers from data: " + (suppliers != null ? suppliers.size() : "null"));
    
        if (null == suppliers && null != data.getExistingEventId()) {
            System.out.println("Attempting to find existing event with ID: " + data.getExistingEventId());
            existingEvent = dbDelegate.findProcurementEventById(data.getExistingEventId()).orElse(null);
            System.out.println("Found existing event: " + (existingEvent != null ? existingEvent.toString() : "null"));
            
            if (null != existingEvent) {
                System.out.println("Getting suppliers for existing event. EventType: " + data.getEventType() + ", TwoStageEvent: " + data.getTwoStageEvent());
                suppliers = eventService.getSuppliers(project, existingEvent, data.getEventType(), data.getTwoStageEvent());
                System.out.println("Suppliers retrieved from existing event: " + (suppliers != null ? suppliers.size() : "null"));
            }
        }
    
        if (null == suppliers) {
            System.out.println("Resolving suppliers using CA Number: " + project.getCaNumber() + " and Lot Number: " + project.getLotNumber());
            suppliers = supplierService.resolveSuppliers(project.getCaNumber(), project.getLotNumber());
            System.out.println("Resolved suppliers count: " + (suppliers != null ? suppliers.size() : "null"));
        }
    
        if(null != suppliers) {
            Map<String, String> options = Map.ofEntries(entry("store", "jaggaer"));
            System.out.println("Created store options: " + options);
            
            SupplierStore store = factory.getStore(event);
            System.out.println("Created supplier store: " + store.getClass().getSimpleName());
            
            try {
                System.out.println("Attempting to store suppliers. Overwrite: " + data.getOverWrite());
                store.storeSuppliers(event, suppliers, data.getOverWrite(), principal, options);
                System.out.println("Successfully stored suppliers");
            } catch(JaggaerApplicationException jae) {
                System.out.println("Caught JaggaerApplicationException: " + jae.getMessage());
                if(jae.getMessage().contains("Code: [-998]")) {
                    System.out.println("Retryable exception detected (-998). Rethrowing...");
                    throw new RetryableException("-998", jae.getMessage(), jae);
                } else {
                    System.out.println("Non-retryable exception. Rethrowing original exception...");
                    throw jae;
                }
            }
            
            String successMessage = "Pushed " + suppliers.size() + " suppliers to Jaggaer";
            log.info("Successfully pushed {} suppliers to project {}, event {}", suppliers.size(), project.getId(), event.getEventID());
            System.out.println(successMessage);
            return successMessage;
        } else {
            String noSuppliersMessage = "No suppliers available for Jaggaer push";
            log.info("No suppliers are synced to project");
            System.out.println(noSuppliersMessage);
            return noSuppliersMessage;
        }
    }

    // @Override
    // @Transactional
    // public String accept(String principal, JaggaerSupplierEventData data) {
    //     var event = getEvent(data);
    //     ProcurementProject project = event.getProject(); //dbDelegate.findProcurementProjectById(data.getProjectId()).orElseThrow();
    //     ProcurementEvent existingEvent = null;
    //     List<Supplier> suppliers = data.getSuppliers();


    //     if (null == suppliers && null != data.getExistingEventId()) {
    //         existingEvent = dbDelegate.findProcurementEventById(data.getExistingEventId()).orElse(null);
    //         if (null != existingEvent) {
    //             suppliers = eventService.getSuppliers(project, existingEvent, data.getEventType(), data.getTwoStageEvent());
    //         }
    //     }

    //     if (null == suppliers) {
    //         suppliers = supplierService.resolveSuppliers(project.getCaNumber(), project.getLotNumber());
    //     }

    //     if(null != suppliers) {
    //         Map<String, String> options = Map.ofEntries(entry("store", "jaggaer"));
    //         SupplierStore store = factory.getStore(event);
    //         try {
    //             store.storeSuppliers(event, suppliers, data.getOverWrite(), principal, options);
    //         }catch(JaggaerApplicationException jae){
    //             if(jae.getMessage().contains("Code: [-998]")){
    //                 throw new RetryableException("-998", jae.getMessage(), jae);
    //             }else
    //                 throw jae;
    //         }
    //         log.info("Successfully pushed {} suppliers to project {}, event {}", suppliers.size(), project.getId(), event.getEventID());
    //         return "Pushed " + suppliers.size() + " suppliers to Jaggaer";
    //     }else{
    //         log.info("No suppliers are synced to project");
    //         return "No suppliers available for Jaggaer push";
    //     }
    // }


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
    public boolean canRetry(String errorCode, RetryableException re) {
        return re.getErrorCode().equals("-998");
    }

    @Override
    public String getIdentifier(JaggaerSupplierEventData data) {
        return data.getEventType() + ":" + data.getProjectId() + "/" + data.getEventId();
    }

    @Override
    public String getTaskName() {
        return "JaggaerSupplierPush";
    }
}
