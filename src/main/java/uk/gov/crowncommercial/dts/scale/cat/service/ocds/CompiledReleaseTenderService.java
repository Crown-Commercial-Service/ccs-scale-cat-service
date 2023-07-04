package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseTenderService{
    private final AgreementsService agreementsService;
    private final JaggaerService jaggaerService;
    private final OcdsConverter ocdsConverter;
    private final RetryableTendersDBDelegate tendersDBDelegate;


    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        Tender1 tender = OcdsHelper.getTender(re);

        CompletableFuture cf = CompletableFuture.runAsync(()->{
            populateLots(pq, tender);
        });
        ProcurementProject pp = pq.getProject();
        tender.setValue(getMaxValue(pp));
        tender.setMinValue(getMinValue(pp));
        tender.setSubmissionMethod(Arrays.asList(SubmissionMethod.ELECTRONICSUBMISSION));
        tender.setTenderPeriod(getTenderPeriod(pp));
        return new MapperResponse(re,cf);
    }

    private Period1 getTenderPeriod(ProcurementProject pp) {
        Period1 period = new Period1();
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        period.setStartDate(OffsetDateTime.ofInstant(pe.getPublishDate(), ZoneId.systemDefault()));
        if(null != pe.getCloseDate())
            period.setEndDate(OffsetDateTime.ofInstant(pe.getCloseDate(), ZoneId.systemDefault()));
        return period;
    }

    private void populateLots(ProjectQuery pq, Tender1 tender) {
        Lot1 l = new Lot1();
        ProcurementProject pp = pq.getProject();
        l.setId(pq.getProject().getLotNumber());
        LotDetail lotDetail = agreementsService.getLotDetails(pp.getCaNumber(), pp.getLotNumber());
        if(null != lotDetail){
            l.setTitle(lotDetail.getName());
            l.setDescription(lotDetail.getDescription());
        }
        tender.setLots(Arrays.asList(l));
    }

    public MapperResponse populateTenderers(Record1 re, ProjectQuery pq) {
        log.debug("populating Tenderers");
        ProcurementProject pp = pq.getProject();
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        Tender1 tender = OcdsHelper.getTender(re);

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ExportRfxResponse rfxResponse = jaggaerService.getRfxWithSuppliers(pe.getExternalEventId());
            List<Supplier>  sdf = rfxResponse.getSuppliersList().getSupplier();
            Set<Integer> bravoIds = sdf.stream().map(t -> t.getCompanyData().getId()).collect(Collectors.toSet());
            Set<OrganisationMapping> orgMappings = tendersDBDelegate.findOrganisationMappingByExternalOrganisationIdIn(bravoIds);
            List<OrganizationReference1> tenderers = rfxResponse.getSuppliersList().getSupplier().stream().map(t -> this.convertSuppliers(t, orgMappings)).toList();


//            List<OrganizationReference1> tenderers = suppliers.stream()
//                    .limit(2) // TODO  remove this artificial limit
//                    .map(this::convertLotSuppliers).toList();
            tender.setTenderers(tenderers);
        });
        return new MapperResponse(re, cf);
    }

    private OrganizationReference1 convertSuppliers(Supplier supplier, Set<OrganisationMapping> orgMappings ) {
        OrganizationReference1 ref = new OrganizationReference1();
        Optional<OrganisationMapping> orgMap = orgMappings.stream().filter(t -> t.getExternalOrganisationId().equals(supplier.getCompanyData().getId())).findFirst();
        if(null != supplier.getCompanyData()) {
            CompanyData cData = supplier.getCompanyData();
            if(orgMap.isPresent()){
                ref.setId(orgMap.get().getCasOrganisationId());
            }
            
            ref.setName(cData.getName());
        }
        return ref;
    }

    public MapperResponse populateDocuments(Record1 re, ProjectQuery pq) {
        log.debug("populating Documents");
        return new MapperResponse(re);
    }

    public MapperResponse populateMilestones(Record1 re, ProjectQuery pq) {
        log.warn("populating Milestones not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateAmendments(Record1 re, ProjectQuery pq) {
        log.warn("populating Amendments not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateEnquiries(Record1 re, ProjectQuery pq) {
        log.warn("populating Enquiries not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateCriteria(Record1 re, ProjectQuery pq) {
        log.debug("populating Criteria");
        Tender1 tender = OcdsHelper.getTender(re);
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pq.getProject());
        DataTemplate template = pe.getProcurementTemplatePayload();
        List<Criterion1> result = template.getCriteria().stream().map((t) -> {return ocdsConverter.convert(t);}).toList();
        tender.setCriteria(result);
        return new MapperResponse(re);
    }

    public MapperResponse populateSelectionCriteria(Record1 re, ProjectQuery pq) {
        log.warn("populating selection Criteria not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateTechniques(Record1 re, ProjectQuery pq) {
        log.warn("populating Techniques not yet implemented");
        return new MapperResponse(re);
    }

    private Value1 getMinValue(ProcurementProject pp) {
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        return getValue1(pe, "Group 18", "Set your budget ", "Question 3");
    }

    private Value1 getMaxValue(ProcurementProject pp) {
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        return getValue1(pe, "Group 18", "Set your budget ", "Question 2");
    }

    private static Value1 getValue1(ProcurementEvent pe, String groupId, String groupDesc, String reqId) {
        String maxBudget = EventsHelper.getData(groupId, groupDesc,  reqId, pe.getProcurementTemplatePayload().getCriteria());
        if(null != maxBudget && maxBudget.trim().length() > 0){
            BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(maxBudget));
            Value1 amount = new Value1();
            amount.amount(bd).currency(Currency1.GBP);
            return amount;
        }
        return null;
    }
}
