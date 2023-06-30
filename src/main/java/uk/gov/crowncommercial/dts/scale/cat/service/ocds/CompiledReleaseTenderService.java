package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseTenderService{
    private final AgreementsService agreementsService;
    private final OcdsConverter ocdsConverter;


    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        Tender1 tender = OcdsHelper.getTender(re);

        CompletableFuture cf = CompletableFuture.runAsync(()->{
            populateLots(pq, tender);
        });

        return new MapperResponse(re,cf);
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
        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ProcurementProject pp = pq.getProject();
            Collection<LotSupplier> suppliers = agreementsService.getLotSuppliers(pp.getCaNumber(), pp.getLotNumber());
            Tender1 tender = OcdsHelper.getTender(re);
            List<OrganizationReference1> tenderers = suppliers.stream()
                    .limit(2) // TODO  remove this artificial limit
                    .map(this::convertLotSuppliers).toList();
            tender.setTenderers(tenderers);
        });
        return new MapperResponse(re, cf);
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

    private OrganizationReference1 convertLotSuppliers(LotSupplier lotSupplier) {
        OrganizationReference1 orgRef = new OrganizationReference1();
        orgRef.id(lotSupplier.getOrganization().getId());
        orgRef.setName(lotSupplier.getOrganization().getName());
        return orgRef;
    }
}
