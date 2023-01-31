package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractSupplierStore implements SupplierStore {

    public static final String ERR_MSG_FMT_SUPPLIER_NOT_FOUND =
            "Organisation id '%s' not found in organisation mappings";

    protected RetryableTendersDBDelegate retryableTendersDBDelegate;
    protected Set<OrganisationMapping> getOrganisationMappings(EventSuppliers eventSuppliers) {
        var supplierOrgIds = eventSuppliers.getSuppliers().stream().map(OrganizationReference1::getId)
                .collect(Collectors.toSet());

        var supplierOrgMappings =
                retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);

        // Validate suppliers exist in Organisation Mapping Table
        if (supplierOrgMappings.size() != eventSuppliers.getSuppliers().size()) {

            var missingSuppliers = new ArrayList<String>();
            eventSuppliers.getSuppliers().stream().forEach(or -> {
                if (supplierOrgMappings.parallelStream()
                        .filter(som -> som.getOrganisationId().equals(or.getId())).findFirst().isEmpty()) {
                    missingSuppliers.add(or.getId());
                }
            });

            if (!missingSuppliers.isEmpty()) {
                throw new ResourceNotFoundException(String.format(
                        "The following suppliers are not present in the Organisation Mappings, so unable to add them: %s",
                        missingSuppliers));
            }
        }
        return supplierOrgMappings;
    }

    protected Set<OrganisationMapping> getOrganisationMappings(List<Supplier> suppliers) {
        Set<Integer>  supplierOrgs = new HashSet<>();
        for(Supplier supplier: suppliers){
            CompanyData data =  supplier.getCompanyData();
            if(null != data)
                supplierOrgs.add(data.getId());
        }

        var supplierOrgMappings =
                retryableTendersDBDelegate.findOrganisationMappingByExternalOrganisationIdIn(supplierOrgs);

        // Validate suppliers exist in Organisation Mapping Table
        if (supplierOrgMappings.size() != supplierOrgs.size()) {

            var missingSuppliers = new ArrayList<String>();
            supplierOrgs.stream().forEach(externalOrgId -> {
                if (supplierOrgMappings.parallelStream()
                        .filter(som -> som.getOrganisationId().equals(externalOrgId)).findFirst().isEmpty()) {
                    missingSuppliers.add(String.valueOf(externalOrgId));
                }
            });

            if (!missingSuppliers.isEmpty()) {
                throw new ResourceNotFoundException(String.format(
                        "The following suppliers are not present in the Organisation Mappings, so unable to add them: %s",
                        missingSuppliers));
            }
        }
        return supplierOrgMappings;
    }


    @Autowired
    public void setRetryableTendersDBDelegate(RetryableTendersDBDelegate retryableTendersDBDelegate){
        this.retryableTendersDBDelegate = retryableTendersDBDelegate;
    }


    protected OrganisationMapping getSupplierOrgMapping(String organisationId) {
        return retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, organisationId)));
    }

    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal, Map<String, String> options) {
        return storeSuppliers(event, suppliers, overWrite, principal);
    }
}
