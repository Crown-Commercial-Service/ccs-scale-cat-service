package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SupplierSelection;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

import javax.validation.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSupplierStore extends AbstractSupplierStore {
    public static final String ERR_MSG_ALL_DIMENSION_WEIGHTINGS =
            "All dimensions must have 100% weightings prior to the supplier(s) can be added to the event";

    private static final String ERR_MSG_SUPPLIER_NOT_FOUND_CONCLAVE =
            "Supplier [%s] not found in Conclave";


    private final AssessmentService assessmentService;
    private final ConclaveService conclaveService;


    @Override
    public EventSuppliers getSuppliers(ProcurementEvent event, String principal) {
        return getSuppliersFromTendersDB(event);
    }

    @Override
    public EventSuppliers storeSuppliers(ProcurementEvent event, EventSuppliers eventSuppliers, String principal) {

        Set<OrganisationMapping> supplierOrgMappings = getOrganisationMappings(eventSuppliers);

        if (eventSuppliers.getJustification() != null) {
            event.setSupplierSelectionJustification(eventSuppliers.getJustification());
        }

        log.debug("Event {} is persisted in Tenders DB only {}", event.getEventID(),
                event.getEventType());

        if(event.isTendersDBOnly()) {
            var assessment =
                    assessmentService.getAssessment(event.getAssessmentId(), Boolean.FALSE, Optional.empty());
            var dimensionWeightingCheck = assessment.getDimensionRequirements().stream()
                    .map(DimensionRequirement::getWeighting).reduce(0, Integer::sum);
            if (dimensionWeightingCheck != 100) {
                throw new ValidationException(ERR_MSG_ALL_DIMENSION_WEIGHTINGS);
            }
        }

        boolean overwrite = Boolean.TRUE.equals(eventSuppliers.getOverwriteSuppliers());
        addSuppliersToTendersDB(event, supplierOrgMappings, overwrite, principal);

        return eventSuppliers;
    }

    @Override
    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal) {
        Set<OrganisationMapping> supplierOrgMappings = getOrganisationMappings(suppliers);

        log.debug("Event {} is persisted in Tenders DB only {}", event.getEventID(),
                event.getEventType());

        if(event.isTendersDBOnly()) {
            var assessment =
                    assessmentService.getAssessment(event.getAssessmentId(), Boolean.FALSE, Optional.empty());
            var dimensionWeightingCheck = assessment.getDimensionRequirements().stream()
                    .map(DimensionRequirement::getWeighting).reduce(0, Integer::sum);
            if (dimensionWeightingCheck != 100) {
                throw new ValidationException(ERR_MSG_ALL_DIMENSION_WEIGHTINGS);
            }
        }
        addSuppliersToTendersDB(event, supplierOrgMappings, overWrite, principal);
        return suppliers;
    }

    private ProcurementEvent addSuppliersToTendersDB(final ProcurementEvent event,
                                                     final Set<OrganisationMapping> supplierOrgMappings, final boolean overwrite,
                                                     final String principal) {
        log.debug("Saving Suppliers for the event {} from Database", event.getId());
        if (overwrite && event.getCapabilityAssessmentSuppliers() != null) {
            event.getCapabilityAssessmentSuppliers()
                    .removeIf(supplierSelection -> supplierSelection.getId() != null);
        }


        Set<SupplierSelection> supplierSelectionSet=supplierOrgMappings.stream().map(org-> {return SupplierSelection.builder().organisationMapping(org).procurementEvent(event)
                .createdAt(Instant.now()).createdBy(principal).build();}).collect(Collectors.toSet());
        if(Objects.nonNull(event.getCapabilityAssessmentSuppliers())){
            event.getCapabilityAssessmentSuppliers().addAll(supplierSelectionSet);
        }else{
            event.setCapabilityAssessmentSuppliers(supplierSelectionSet);
        }

        return retryableTendersDBDelegate.save(event);
    }

    @Override
    public void deleteSupplier(ProcurementEvent event, String organisationId, String principal) {
        log.debug("Deleting Supplier {} for the event {} from Database", organisationId, event.getId());
        var supplierOrgMapping = getSupplierOrgMapping(organisationId);

        event.setUpdatedAt(Instant.now());
        event.setUpdatedBy(principal);
        retryableTendersDBDelegate.save(event);

        var supplierSelection = event.getCapabilityAssessmentSuppliers().stream()
                .filter(s -> s.getOrganisationMapping().getId().equals(supplierOrgMapping.getId()))
                .findFirst().orElseThrow();

        retryableTendersDBDelegate.delete(supplierSelection);
    }

    @Override
    public List<Supplier> getSuppliers(ProcurementEvent event) {
        var supplierOrgIds = getSuppliersFromTendersDB(event).getSuppliers().stream()
                .map(OrganizationReference1::getId).collect(Collectors.toSet());

        return retryableTendersDBDelegate
                .findOrganisationMappingByOrganisationIdIn(supplierOrgIds).stream().map(org -> {
                    var companyData = CompanyData.builder().id(org.getExternalOrganisationId()).build();
                    return Supplier.builder().companyData(companyData).build();
                }).collect(Collectors.toList());

    }

    private EventSuppliers getSuppliersFromTendersDB(final ProcurementEvent event) {
        log.debug("Getting Suppliers from Database");
        var suppliers = event.getCapabilityAssessmentSuppliers().stream().map(s -> {
            var orgIdentity =
                    conclaveService.getOrganisationIdentity(s.getOrganisationMapping().getOrganisationId());

            var orgRef = new OrganizationReference1().id(s.getOrganisationMapping().getOrganisationId());
            orgIdentity.ifPresentOrElse(or -> orgRef.name(or.getIdentifier().getLegalName()),
                    () -> log.warn(String.format(ERR_MSG_SUPPLIER_NOT_FOUND_CONCLAVE,
                            s.getOrganisationMapping().getOrganisationId())));
            return orgRef;
        }).collect(Collectors.toList());
        return new EventSuppliers().suppliers(suppliers)
                .justification(event.getSupplierSelectionJustification());
    }

}
