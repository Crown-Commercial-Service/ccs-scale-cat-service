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

@Component
@RequiredArgsConstructor
public class OcdsRecordMapper  implements InitializingBean {
    private Map<String, ProjectRecordHandler> handlers;
    private final CompiledReleaseService releaseService;
    private final CompiledReleasePlanningService compiledPlanningService;
    private final CompiledReleaseTenderService compiledTenderService;
    private final CompiledReleaseAwardsService compiledAwardsService;


    public Record1 populate(ProjectQuery query, Record1 record){
        Record1 result = record;
        List<CompletableFuture> cfs = new ArrayList<>();
        for(String section: query.getSections()){
            ProjectRecordHandler handler = handlers.get(section);
            if(null != handler){
                MapperResponse response = handler.apply(query, result);
                result = response.getRecord();
                if(null != response.getCompletableFuture()){
                    cfs.add(response.getCompletableFuture());
                }
            }
        }
        if(cfs.size() > 0) {
            CompletableFuture[] sdf = cfs.toArray(new CompletableFuture[cfs.size()]);
            CompletableFuture.allOf(sdf).join();
        }

        return result;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        handlers = new HashMap<>();

        handlers.put(OcdsSections.COMPILED_RELEASE, (pq, re) -> {
            return releaseService.populate(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_PARTIES, (pq, re) -> {
            return releaseService.populateParties(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_BUYER, (pq, re) -> {
            return releaseService.populateBuyer(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_CONTRACTS, (pq, re) -> {
            return releaseService.populateContracts(re, pq);
        });

        mapCompiledReleasePlanning();

        mapCompiledReleaseTender();

        mapCompiledReleaseAward();
    }

    private void mapCompiledReleasePlanning() {
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING,  (pq, re) -> {
            return compiledPlanningService.populateGeneral(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_BUDGET,  (pq, re) -> {
            return compiledPlanningService.populateBudget(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_DOCUMENTS,  (pq, re) -> {
            return compiledPlanningService.populateDocuments(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_PLANNING_MILESTONES,  (pq, re) -> {
            return compiledPlanningService.populateMilestones(re, pq);
        });
    }

    private void mapCompiledReleaseTender() {
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER,  (pq, re) -> {
            return compiledTenderService.populateGeneral(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_TENDERERS,  (pq, re) -> {
            return compiledTenderService.populateTenderers(re, pq);
        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_DOCUMENTS,  (pq, re) -> {
//            return compiledTenderService.populateDocuments(re, pq);
//        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_MILESTONES,  (pq, re) -> {
//            return compiledTenderService.populateMilestones(re, pq);
//        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_AMENDMENTS,  (pq, re) -> {
//            return compiledTenderService.populateAmendments(re, pq);
//        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_ENQUIRIES,  (pq, re) -> {
//            return compiledTenderService.populateEnquiries(re, pq);
//        });
        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_CRITERIA,  (pq, re) -> {
            return compiledTenderService.populateCriteria(re, pq);
        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_SELECTIONCRITERIA,  (pq, re) -> {
//            return compiledTenderService.populateSelectionCriteria(re, pq);
//        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_TENDER_TECHNIQUES,  (pq, re) -> {
//            return compiledTenderService.populateTechniques(re, pq);
//        });
    }

    private void mapCompiledReleaseAward() {
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS,  (pq, re) -> {
            return compiledAwardsService.populateGeneral(re, pq);
        });
//        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_SUPPLIERS,  (pq, re) -> {
//            return compiledAwardsService.populateSuppliers(re, pq);
//        });
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_ITEMS,  (pq, re) -> {
            return compiledAwardsService.populateItems(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_DOCUMENTS,  (pq, re) -> {
            return compiledAwardsService.populateDocuments(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_AMENDMENTS,  (pq, re) -> {
            return compiledAwardsService.populateAmendments(re, pq);
        });
        handlers.put(OcdsSections.COMPILED_RELEASE_AWARDS_REQRESPONSES,  (pq, re) -> {
            return compiledAwardsService.populateRequiremetResponses(re, pq);
        });
    }
}
