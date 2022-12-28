package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RPAGenericService {

  private final WebClient rpaServiceWebClient;
  private final WebclientWrapper webclientWrapper;
  private final ObjectMapper objectMapper;
  private final RPAAPIConfig rpaAPIConfig;
  private final JaggaerService jaggaerService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final BuyerUserDetailsRepo buyerDetailsRepo;

  /**
   * @param procurementEvent
   * @return suppliers as a string
   */
  public Pair<List<Supplier>, Set<OrganisationMapping>> getValidSuppliers(
      final ProcurementEvent procurementEvent, final List<String> orgIds) {
    var supplierOrgIds = orgIds.stream().collect(Collectors.toSet());
    // Retrieve and verify Tenders DB org mappings
    var supplierOrgMappings =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      var errorDesc =
          String.format("No supplier organisation mappings found in Tenders DB %s", supplierOrgIds);
      log.error(errorDesc);
      throw new JaggaerRPAException(errorDesc);
    }

    // Find all externalOrgIds
    var supplierExternalIds = supplierOrgMappings.stream()
        .map(OrganisationMapping::getExternalOrganisationId).collect(Collectors.toSet());

    // Find actual suppliers of project and event
    var suppliers = jaggaerService.getRfxWithSuppliers(procurementEvent.getExternalEventId()).getSuppliersList()
        .getSupplier();

    // Find all unmatched org ids
    var unMatchedSuppliers = supplierExternalIds.stream()
        .filter(orgid -> suppliers.stream()
            .noneMatch(supplier -> supplier.getCompanyData().getId().equals(orgid)))
        .toList();

    if (!isEmpty(unMatchedSuppliers)) {
      var errorDesc =
          String.format("Supplied organisation mappings not matched with actual suppliers '%s'",
              unMatchedSuppliers);
      log.error(errorDesc);
      throw new JaggaerRPAException(errorDesc);
    }

    // Comparing the requested organisation ids and event suppliers info
    var matchedSuppliers = suppliers.stream()
        .filter(supplier -> supplierExternalIds.stream()
            .anyMatch(orgId -> orgId.equals(supplier.getCompanyData().getId())))
        .toList();
    
    return Pair.of(
        matchedSuppliers.stream()
            .map(e -> Supplier.builder().companyData(e.getCompanyData())
                .id(String.valueOf(e.getCompanyData().getId())).build())
            .toList(),
        supplierOrgMappings);
  }


  public OffsetDateTime handleDSTDate(OffsetDateTime offsetDate, String zoneId) {
    var zonedDateTimeBefore = offsetDate.atZoneSameInstant(ZoneId.of(zoneId));
    return zonedDateTimeBefore.toOffsetDateTime();
  }
}
