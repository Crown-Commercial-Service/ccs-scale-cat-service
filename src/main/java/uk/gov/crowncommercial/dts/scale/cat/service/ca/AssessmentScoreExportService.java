package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.SupplierScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentScoreExportService {
    private final AssessmentService assessmentService;
    private final AgreementsService agreementService;
    private final ProcurementProjectRepo ppRepo;

    public List<SupplierScore> getScores(final Integer assessmentId,
                                         final Float minScore, final Float maxScore,
                                         final Optional<String> principalForScores){

        Assessment assessment = assessmentService.getAssessment(assessmentId, true, principalForScores);
        List<SupplierScores> calculatedScores = assessment.getScores();
//        ProcurementProject project = ppRepo.findDistinctByProcurementEventsAssessmentId(assessmentId);
//        Collection<LotSupplier> suppliers = agreementService.getLotSuppliers(project.getCaNumber(),  project.getLotNumber());

     //   Map<String, LotSupplier> supplierMap = suppliers.stream().collect(Collectors.toMap(LotSupplier::getOrganization))

        Predicate<SupplierScores>  filter = getFilter(minScore, maxScore);

        List<SupplierScore> scoreList = calculatedScores.stream().filter(filter)
                .map(this::convert)
                .toList();
        return scoreList;
    }

    public SupplierScore convert(SupplierScores input){
        SupplierScore output = new SupplierScore();
        output.setScore(input.getTotal());
        output.setIdentifier(input.getSupplier().getId());
        return output;
    }

    private Predicate<SupplierScores> getFilter(Float minScore, Float maxScore){

        if(null != minScore){
            if(null != maxScore){
                return new MinMaxScorePredicate(minScore, maxScore);
            }else
                return new MinScorePredicate(minScore);
        }else {
            if(null != maxScore)
                return new MaxScorePredicate(maxScore);
            else
                return new NoopPredicate();
        }
    }

    private static class MinScorePredicate implements Predicate<SupplierScores>{
        private final double minScore;
        public MinScorePredicate(double minScore){
            this.minScore = minScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score >= minScore;
        }
    }

    private static class MaxScorePredicate implements Predicate<SupplierScores>{
        private final double maxScore;
        public MaxScorePredicate(double maxScore){
            this.maxScore = maxScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score <= maxScore;
        }
    }

    private static class MinMaxScorePredicate implements Predicate<SupplierScores>{
        private final double maxScore, minScore;
        public MinMaxScorePredicate(double minScore, double maxScore){
            this.maxScore = maxScore;
            this.minScore = minScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score >= minScore && score <= maxScore;
        }
    }

    private static class NoopPredicate implements Predicate<SupplierScores>{
        @Override
        public boolean test(SupplierScores supplierScores) {
            return true;
        }
    }
}
