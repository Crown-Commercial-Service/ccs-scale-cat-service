package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.QuestionAndAnswerService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseTenderService extends AbstractOcdsService {
    private final AgreementsService agreementsService;
    private final OcdsConverter ocdsConverter;
    private final RetryableTendersDBDelegate tendersDBDelegate;
    private final QuestionAndAnswerService questionAndAnswerService;


    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        Tender1 tender = OcdsHelper.getTender(re);

        ProcurementProject pp = pq.getProject();
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        List<TemplateCriteria> criters = pe.getProcurementTemplatePayload().getCriteria();
        tender.setDescription(EventsHelper.getData("Criterion 3", "Group 3", "Question 1", pe.getProcurementTemplatePayload().getCriteria()));
        tender.setTitle(pp.getProjectName());
        tender.setValue(getMaxValue(pp));
        tender.setMinValue(getMinValue(pp));
        tender.setSubmissionMethod(Arrays.asList(SubmissionMethod.ELECTRONICSUBMISSION));

        CompletableFuture cf = CompletableFuture.runAsync(() -> {
            populateStatusAndPeriod(tender, pp, pq);
            populateLots(pq, tender);
        });
        return new MapperResponse(re, cf);
    }

    @SneakyThrows
    private void populateStatusAndPeriod(Tender1 tender, ProcurementProject pp, ProjectQuery pq) {
        ProcurementEvent pe = EventsHelper.getLastPublishedEvent(pp);
        ExportRfxResponse exportRfxResponse = getLatestRFXWithSuppliers(pq);

        ExportRfxResponse firstRfxResponse = getFirstRFXWithSuppliers(pq);

        if (null != exportRfxResponse) {
            TenderStatus status = EventStatusHelper.getStatus(exportRfxResponse.getRfxSetting(), pe);
            tender.setStatus(status);
            tender.setTenderPeriod(getTenderPeriod(pp, firstRfxResponse.getRfxSetting()));
        }
    }

    private Period1 getTenderPeriod(ProcurementProject pp, RfxSetting rfxSetting) {
        Period1 period = new Period1();
        period.setStartDate(rfxSetting.getPublishDate());
        if (null != rfxSetting.getCloseDate())
            period.setEndDate(rfxSetting.getCloseDate());
        return period;
    }

    private void populateLots(ProjectQuery pq, Tender1 tender) {
        Lot1 l = new Lot1();
        ProcurementProject pp = pq.getProject();
        l.setId(pq.getProject().getLotNumber());
        LotDetail lotDetail = agreementsService.getLotDetails(pp.getCaNumber(), pp.getLotNumber());
        if (null != lotDetail) {
            l.setTitle(lotDetail.getName());
            l.setDescription(lotDetail.getDescription());
        }
        tender.setLots(Arrays.asList(l));
    }

    public MapperResponse populateTenderers(Record1 re, ProjectQuery pq) {
        log.debug("populating Tenderers");
        ProcurementProject pp = pq.getProject();

        Tender1 tender = OcdsHelper.getTender(re);

        CompletableFuture cf = CompletableFuture.runAsync(() -> {
            ExportRfxResponse rfxResponse = getFirstRFXWithSuppliers(pq);
            List<Supplier> sdf = rfxResponse.getSuppliersList().getSupplier();
            Set<Integer> bravoIds = sdf.stream().map(t -> t.getCompanyData().getId()).collect(Collectors.toSet());
            Set<OrganisationMapping> orgMappings = tendersDBDelegate.findOrganisationMappingByExternalOrganisationIdIn(bravoIds);
            List<OrganizationReference1> tenderers = rfxResponse.getSuppliersList().getSupplier().stream().map(t -> this.convertSuppliers(t, orgMappings)).toList();
            tender.setTenderers(tenderers);
        });
        return new MapperResponse(re, cf);
    }

    private OrganizationReference1 convertSuppliers(Supplier supplier, Set<OrganisationMapping> orgMappings) {
        OrganizationReference1 ref = new OrganizationReference1();
        Optional<OrganisationMapping> orgMap = orgMappings.stream().filter(t -> t.getExternalOrganisationId().equals(supplier.getCompanyData().getId())).findFirst();
        if (null != supplier.getCompanyData()) {
            CompanyData cData = supplier.getCompanyData();
            if (orgMap.isPresent()) {
                ref.setId(orgMap.get().getCasOrganisationId());
            }
            ref.setContactPoint(new ContactPoint1().name(supplier.getStatusCode() + ":" + supplier.getStatus()));
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
        log.debug("populating Enquiries by Q and A");
        ProcurementProject pp = pq.getProject();
        Tender1 tender = OcdsHelper.getTender(re);
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        QandAWithProjectDetails qandAWithProjectDetails = questionAndAnswerService.getQuestionAndAnswerForSupplierByEvent(pp.getId(), EventsHelper.getEventId(pe));
        tender.setEnquiries(qandAWithProjectDetails.getQandA().stream().map(t -> convertQA(t)).toList());
        return new MapperResponse(re);
    }

    private Enquiry1 convertQA(QandA t) {
        Enquiry1 result = new Enquiry1();
        result.setTitle(t.getQuestion());
        result.setAnswer(t.getAnswer());
        result.setDate(t.getCreated());

        if (null != t.getLastUpdated())
            result.setDateAnswered(t.getLastUpdated());
        else
            result.setDateAnswered(t.getCreated());

        result.setId(String.valueOf(t.getId()));
        return result;
    }

    public MapperResponse populateCriteria(Record1 re, ProjectQuery pq) {
        log.debug("populating Criteria");
        Tender1 tender = OcdsHelper.getTender(re);
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pq.getProject());
        DataTemplate template = pe.getProcurementTemplatePayload();
        List<Criterion1> result = template.getCriteria().stream().map((t) -> {
            return ocdsConverter.convert(t);
        }).toList();
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
        return getValue1(pe, "Criterion 3", "Group 18", "Question 3");
    }

    private Value1 getMaxValue(ProcurementProject pp) {
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        return getValue1(pe, "Criterion 3", "Group 18", "Question 2");
    }

    private static Value1 getValue1(ProcurementEvent pe, String criterionId, String groupId, String reqId) {
        String maxBudget = EventsHelper.getData(criterionId, groupId, reqId, pe.getProcurementTemplatePayload().getCriteria());
        if (null != maxBudget && maxBudget.trim().length() > 0) {
            BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(maxBudget));
            Value1 amount = new Value1();
            amount.amount(bd).currency(Currency1.GBP);
            return amount;
        }
        return null;
    }
}
