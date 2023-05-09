package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import javax.validation.ValidationException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JaggaerSupplierStore extends AbstractSupplierStore {

    private final JaggaerService jaggaerService;

    @Override
    public EventSuppliers getSuppliers(ProcurementEvent event, String principal) {
        log.debug("Getting Suppliers from Jaggaer");
        var existingRfx = jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
        var orgs = new ArrayList<OrganizationReference1>();



        if (existingRfx.getSuppliersList().getSupplier() != null) {
            Set<OrganisationMapping> orgMappings = getOrganisationMappings(existingRfx.getSuppliersList().getSupplier());

            HashMap<Integer, OrganisationMapping> orgMaps = new HashMap<>();
            for(OrganisationMapping orgMap: orgMappings){
                orgMaps.put(orgMap.getExternalOrganisationId(), orgMap);
            }

            existingRfx.getSuppliersList().getSupplier().stream().map(s -> {

                OrganisationMapping om = orgMaps.get(s.getCompanyData().getId());
                if(null == om ){
                    throw new IllegalArgumentException(
                            String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, s.getCompanyData().getId()));
                }

                return new OrganizationReference1().id(String.valueOf(om.getCasOrganisationId()))
                        .name(s.getCompanyData().getName());
            }).forEachOrdered(orgs::add);
        }

        return new EventSuppliers().suppliers(orgs)
                .justification(event.getSupplierSelectionJustification());
    }


    @Override
    public EventSuppliers storeSuppliers(ProcurementEvent event, EventSuppliers eventSuppliers, String principal) {

        Set<OrganisationMapping> supplierOrgMappings = getOrganisationMappings(eventSuppliers);

        if (eventSuppliers.getJustification() != null) {
            event.setSupplierSelectionJustification(eventSuppliers.getJustification());
        }

        log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());

        boolean overwrite = Boolean.TRUE.equals(eventSuppliers.getOverwriteSuppliers());
        addSuppliersToJaggaer(event, supplierOrgMappings, overwrite, principal);

        return eventSuppliers;
    }

    @Override
    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliersList, boolean overWrite, String principal) {
        OperationCode operationCode = overWrite ? OperationCode.UPDATE_RESET : OperationCode.CREATEUPDATE;

        // Build Rfx and update
        var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
                .rfxReferenceCode(event.getExternalReferenceId()).build();
        var rfx = Rfx.builder().rfxSetting(rfxSetting)
                .suppliersList(SuppliersList.builder().supplier(suppliersList).build()).build();
        jaggaerService.createUpdateRfx(rfx, operationCode);
        return suppliersList;
    }

    @Override
    public void deleteSupplier(ProcurementEvent event, String organisationId, String principal) {
        var supplierOrgMapping = getSupplierOrgMapping(organisationId);
        log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());

        var existingRfx = jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
        List<Supplier> updatedSuppliersList = existingRfx.getSuppliersList().getSupplier().stream()
                .filter(
                        s -> !s.getCompanyData().getId().equals(supplierOrgMapping.getExternalOrganisationId()))
                .collect(Collectors.toList());
        var suppliersList = SuppliersList.builder().supplier(updatedSuppliersList).build();

        // Build Rfx and update
        var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
                .rfxReferenceCode(event.getExternalReferenceId()).build();
        var rfx = Rfx.builder().rfxSetting(rfxSetting).suppliersList(suppliersList).build();
        jaggaerService.createUpdateRfx(rfx, OperationCode.UPDATE_RESET);
    }

    @Override
    public List<Supplier> getSuppliers(ProcurementEvent event) {
        log.debug("Getting Suppliers from Jaggaer");
        var existingRfx = jaggaerService.getRfxWithSuppliers(event.getExternalEventId());

        if (existingRfx.getSuppliersList().getSupplier() != null) {
            var supplierOrgIds = existingRfx.getSuppliersList().getSupplier().stream().map(s -> s.getCompanyData().getId()).collect(Collectors.toSet());
            return supplierOrgIds.stream().map(orgId -> {
                var companyData = CompanyData.builder().id(orgId).build();
                return Supplier.builder().companyData(companyData).build();
            }).collect(Collectors.toList());
        }
        return null;
    }

    private void addSuppliersToJaggaer(final ProcurementEvent event,
                                       final Set<OrganisationMapping> supplierOrgMappings, final boolean overwrite, String principal) {
        List<Supplier> suppliersList = supplierOrgMappings.stream().map(org -> {
            var companyData = CompanyData.builder().id(org.getExternalOrganisationId()).build();
            return Supplier.builder().companyData(companyData).build();
        }).collect(Collectors.toList());

        storeSuppliers(event, suppliersList, overwrite, principal);
    }
}
