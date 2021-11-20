package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Encapsulates operations related to suppliers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;

  /**
   * Retrieves suppliers from the Agreements Service based on the CA and Lot and resolves each to a
   * Jaggaer {@link Supplier} object via the organisation mapping table.
   *
   * @param agreementId
   * @param lotId
   * @return the list of suppliers for the given CA/Lot
   * @throws AgreementsServiceApplicationException if no suppliers are found in AS for given CA/Lot
   * @throws TendersDBDataException if not supplier org mappings are found in the mapping table
   */
  public List<Supplier> resolveSuppliers(final String agreementId, final String lotId) {

    // Retrieve and verify AS suppliers
    var lotSuppliers = agreementsService.getLotSuppliers(agreementId, lotId);
    if (isEmpty(lotSuppliers)) {
      log.warn("No Lot Suppliers found in AS for CA: '{}', Lot: '{}'", agreementId, lotId);
      return Collections.emptyList();
    }

    var supplierOrgIds =
        lotSuppliers.stream().map(ls -> ls.getOrganization().getId()).collect(Collectors.toSet());

    // Retrieve and verify Tenders DB org mappings
    var supplierOrgMappings =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      log.warn("No supplier org mappings found in Tenders DB for CA: '{}', Lot: '{}'", agreementId,
          lotId);
      return Collections.emptyList();
    }

    var supplierExternalIds =
        supplierOrgMappings.stream().map(OrganisationMapping::getExternalOrganisationId)
            .map(String::valueOf).collect(Collectors.toSet());

    return supplierExternalIds.stream().map(id -> new Supplier(new CompanyData(id)))
        .collect(Collectors.toList());
  }

}
