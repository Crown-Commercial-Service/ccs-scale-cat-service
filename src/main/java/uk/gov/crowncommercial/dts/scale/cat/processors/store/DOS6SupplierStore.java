package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DOS6SupplierStore implements SupplierStore {

    private final JaggaerSupplierStore jaggaerSupplierStore;
    private final DatabaseSupplierStore databaseSupplierStore;


    private SupplierStore getSupplierStore(ProcurementEvent event, Map<String, String> options) {

        SupplierStore store = getSupplierStore(options);
        if(null != store)
            return store;

        Instant publishDate = event.getPublishDate();
        if (null != publishDate && publishDate.isBefore(Instant.now())) {
            log.debug("Choosing database supplier store to manage the suppliers");
            return databaseSupplierStore;
        }
        else {
            log.debug("Choosing Jaggaer supplier store to manage the suppliers");
            return jaggaerSupplierStore;
        }
    }

    private SupplierStore getSupplierStore(Map<String, String> options){
        if(null != options){
            String store = options.get("store");
            if(null != store){
                switch (store){
                    case "jaggaer":
                        return jaggaerSupplierStore;
                    case "database":
                        return databaseSupplierStore;
                    default:
                        return null;
                }
            }
        }
        return null;
    }

    @Override
    public EventSuppliers getSuppliers(ProcurementEvent event, String principal) {
        Instant publishDate = event.getPublishDate();
        if (null != publishDate && publishDate.isBefore(Instant.now())) {
            log.debug("Choosing database supplier store to retrieve the suppliers");
            EventSuppliers result = databaseSupplierStore.getSuppliers(event, principal);
            if(null != result.getSuppliers() && result.getSuppliers().size() > 0){
                return result;
            }else{
                log.debug("No suppliers found in database, retrieve from Jaggaer");
            }
        }

        log.debug("Choosing Jaggaer supplier store to retrieve the suppliers");
        return jaggaerSupplierStore.getSuppliers(event, principal);
    }

    @Override
    public EventSuppliers storeSuppliers(ProcurementEvent event, EventSuppliers eventSuppliers, String principal) {
        return getSupplierStore(event, null).storeSuppliers(event, eventSuppliers, principal);
    }

    @Override
    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal) {
        return getSupplierStore(event, null).storeSuppliers(event, suppliers,overWrite, principal);
    }

    @Override
    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal, Map<String, String> options) {
        return getSupplierStore(event, options).storeSuppliers(event, suppliers,overWrite, principal);
    }

    @Override
    public void deleteSupplier(ProcurementEvent event, String organisationId, String principal) {
        getSupplierStore(event, null).deleteSupplier(event, organisationId, principal);
    }

    @Override
    public List<Supplier> getSuppliers(ProcurementEvent event) {

        Instant publishDate = event.getPublishDate();
        if (null != publishDate && publishDate.isBefore(Instant.now())) {
            log.debug("Choosing database supplier store to retrieve the suppliers");
            List<Supplier> result = databaseSupplierStore.getSuppliers(event);
            if(null != result && result.size() > 0){
                return result;
            }else{
                log.debug("No suppliers found in database, retrieve from Jaggaer");
            }
        }

        log.debug("Choosing Jaggaer supplier store to retrieve the suppliers");
        return jaggaerSupplierStore.getSuppliers(event);
    }
}
