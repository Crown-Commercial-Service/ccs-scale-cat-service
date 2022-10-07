package uk.gov.crowncommercial.dts.scale.cat.cache;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class StaticDataCacheManager implements CacheManagerCustomizer<ConcurrentMapCacheManager> {


    @Override
    public void customize(ConcurrentMapCacheManager cacheManager) {

        List<String> agreementCacheList=List.of("getAgreementDetails", "getLotEventTypes","getLotEventTypes");
        List<String> conclaveServiceCacheList=List.of("getOrganisation","getUserProfile","getUserContacts","getOrganisationIdentity");
        List<String> dbOrgMappingCacheList=List.of("findOrganisationMappingByOrganisationId","findOrganisationMappingByExternalOrganisationId","findOrganisationMappingByOrganisationIdIn");

        cacheManager.setCacheNames( Stream.of(agreementCacheList, conclaveServiceCacheList,dbOrgMappingCacheList)
                .flatMap(Collection::stream).toList());
    }



}
