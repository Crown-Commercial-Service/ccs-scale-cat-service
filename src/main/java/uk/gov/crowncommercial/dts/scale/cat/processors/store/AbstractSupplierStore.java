package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public abstract class AbstractSupplierStore implements SupplierStore {

    public static final String ERR_MSG_FMT_SUPPLIER_NOT_FOUND =
            "Organisation id '%s' not found in organisation mappings";

    protected RetryableTendersDBDelegate retryableTendersDBDelegate;

    /**
     * Gets the organisation mappings of a set of suppliers passed through to us
     */
    protected Set<OrganisationMapping> getOrganisationMappings(EventSuppliers eventSuppliers) {
        // Pull together the supplier org IDs of the suppliers passed in
        Set<String> supplierOrgIds = eventSuppliers.getSuppliers().stream().map(OrganizationReference1::getId)
                .collect(Collectors.toSet());

        // Next grab the organisation mappings from the Tenders DB for those IDs
        Set<OrganisationMapping> supplierOrgMappings = retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationIdIn(supplierOrgIds);

        // Our two lists should now be the same size - check this
        if (supplierOrgMappings.size() != eventSuppliers.getSuppliers().size()) {
            // There is a disparity, which means some suppliers don't exist in the Tenders org mapping table.  Work out which ones
            List<String> missingSuppliers = new ArrayList<>();
            eventSuppliers.getSuppliers().forEach(supplier -> {
                if (supplierOrgMappings.parallelStream()
                        .filter(som -> som.getCasOrganisationId().equals(supplier.getId())).findFirst().isEmpty()) {
                    missingSuppliers.add(supplier.getId());
                }
            });

            if (!missingSuppliers.isEmpty()) {
                // We now have a list of the missing suppliers.  We probably want to log that we have this issue, but then just strip them out of the response and carry on
                log.warn(String.format(
                        "The following suppliers are not present in the Organisation Mappings, so unable to add them: %s",
                        missingSuppliers));
            }
        }

        // We can now return our mappings, confident that they exist in the correct state
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
            for(OrganisationMapping supplier:supplierOrgMappings){
                if(supplierOrgs.contains(supplier.getExternalOrganisationId())){
                    supplierOrgs.remove(supplier.getExternalOrganisationId());
                }
            }

            if (!supplierOrgs.isEmpty()) {
                throw new ResourceNotFoundException(String.format(
                        "The following Jaggaer suppliers are not present in the Organisation Mappings, so unable to add them: BravoIds: %s",
                        supplierOrgs));
            }
        }
        return supplierOrgMappings;
    }


    @Autowired
    public void setRetryableTendersDBDelegate(RetryableTendersDBDelegate retryableTendersDBDelegate){
        this.retryableTendersDBDelegate = retryableTendersDBDelegate;
    }


    protected OrganisationMapping getSupplierOrgMapping(String organisationId) {
        return retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationId(organisationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, organisationId)));
    }

    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal, Map<String, String> options) {
        return storeSuppliers(event, suppliers, overWrite, principal);
    }
}
