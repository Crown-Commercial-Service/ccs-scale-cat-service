package uk.gov.crowncommercial.dts.scale.cat.cache;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Component
public class StaticDataCacheManager implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

  @Override
  public void customize(ConcurrentMapCacheManager cacheManager) {

    List<String> agreementCacheList =
        List.of(
            "getAgreementDetails",
            "getLotDetails",
            "getLotEventTypes",
            "getLotEventTypeDataTemplates",
            "getLotSuppliers");
    List<String> conclaveServiceCacheList =
        List.of("getOrganisation", "getUserProfile", "getUserContacts", "getOrganisationIdentity");
    List<String> dbOrgMappingCacheList =
        List.of(
            "findOrganisationMappingByOrganisationId",
            "findOrganisationMappingByExternalOrganisationId",
            "findOrganisationMappingByOrganisationIdIn",
            "isExternalToolIdValidForGcloud");

    cacheManager.setCacheNames(
        Stream.of(agreementCacheList, conclaveServiceCacheList, dbOrgMappingCacheList)
            .flatMap(Collection::stream)
            .toList());
  }
}
