package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Looks to map OCDS question and answer data to fixed data structs for Project Detail fetches
 */
@Component
@RequiredArgsConstructor
public class OcdsRecordMapper  implements InitializingBean {
    private Map<String, ProjectRecordHandler> handlers;
    private final CompiledReleaseService releaseService;
    private final CompiledReleasePlanningService compiledPlanningService;
    private final CompiledReleaseTenderService compiledTenderService;
    private final CompiledReleaseAwardsService compiledAwardsService;
    private final StatisticsService statisticsService;

    /**
     * Runs all necessary population requests for the data record in an async manner via CompletableFutures
     * This is the core method run when the mapper is used
     */
    public Record1 populate(ProjectQuery query, Record1 record){
        // Worth noting this is not the appropriate way to use CompletableFutures so we should look to refactor this when possible
        Record1 result = record;
        List<CompletableFuture> cfs = new ArrayList<>();

        // For each section of the requested query object, run all relevant mappings dictated by the handlers config
        if (query != null && query.getSections() != null && !query.getSections().isEmpty()) {
            for (String section : query.getSections()) {
                ProjectRecordHandler handler = handlers.get(section);

                if (handler != null) {
                    MapperResponse response = handler.apply(query, result);
                    result = response.getRecord();

                    if (response.getCompletableFuture() != null) {
                        cfs.add(response.getCompletableFuture());
                    }
                }
            }
        }

        if(!cfs.isEmpty()) {
            // Wait for any aync requests to complete before we move on
            CompletableFuture[] sdf = cfs.toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(sdf).join();
        }

        return result;
    }

    /**
     * Sets up the handlers configuration for this mapping service, so that requests know what it is to try and map
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // Define via handlers all the mapping tasks that should be run for a request
        handlers = new HashMap<>();

        handlers.put(OcdsSections.COMPILED_RELEASE, (pq, re) -> releaseService.populate(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_PARTIES, (pq, re) -> releaseService.populateParties(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_BUYER, (pq, re) -> releaseService.populateBuyer(re, pq));

        mapCompiledReleasePlanning();
        mapCompiledReleaseTender();
        mapCompiledReleaseAward();
        mapCompiledReleaseStatistics();
    }

    /**
     * Configure handler instructions for statistics mapping tasks
     */
    private void mapCompiledReleaseStatistics() {
        handlers.put(OcdsSections.COMPILED_RELEASE_STATISTICS,  (pq, re) -> statisticsService.populate(re, pq));
    }

    /**
     * Configure handler instructions for planning mapping tasks
     */
    private void mapCompiledReleasePlanning() {
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING,  (pq, re) -> compiledPlanningService.populateGeneral(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_BUDGET,  (pq, re) -> compiledPlanningService.populateBudget(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_DOCUMENTS,  (pq, re) -> compiledPlanningService.populateDocuments(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_MILESTONES,  (pq, re) -> compiledPlanningService.populateMilestones(re, pq));
    }

    /**
     * Configure handler instructions for release tender mapping tasks
     */
    private void mapCompiledReleaseTender() {
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER,  (pq, re) -> compiledTenderService.populateGeneral(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_TENDERERS,  (pq, re) -> compiledTenderService.populateTenderers(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_ENQUIRIES,  (pq, re) -> compiledTenderService.populateEnquiries(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_CRITERIA,  (pq, re) -> compiledTenderService.populateCriteria(re, pq));
    }

    /**
     * Configure handler instructions for release award mapping tasks
     */
    private void mapCompiledReleaseAward() {
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS,  (pq, re) -> compiledAwardsService.populateGeneral(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_ITEMS,  (pq, re) -> compiledAwardsService.populateItems(re, pq));
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_REQRESPONSES,  (pq, re) -> compiledAwardsService.populateRequiremetResponses(re, pq));
    }
}