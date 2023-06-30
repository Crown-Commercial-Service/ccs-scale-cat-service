package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Award2;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStoreFactory;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseAwardsService {
    private final SupplierStoreFactory supplierStoreFactory;
    private final JaggaerService jaggaerService;
    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        ProcurementProject pp = pq.getProject();

        ProcurementEvent pe = EventsHelper.getAwardEvent(pp);

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ExportRfxResponse rfxResponse = jaggaerService.getRfxWithSuppliers(pe.getExternalEventId());

            if(500 == rfxResponse.getRfxSetting().getStatusCode()){
                Award2 award = getAward(re);
                award.setTitle(pp.getProjectName());
                award.setDescription(null);
                award.setDate(null);
                award.setValue(null);
            }
        });
        return new MapperResponse(re, cf);
    }

    public MapperResponse populateSuppliers(Record1 re, ProjectQuery pq) {
        log.debug("populating Tenderers");
        ProcurementProject pp = pq.getProject();
        Award2 award = getAward(re);
        ProcurementEvent event = EventsHelper.getAwardEvent(pp);
        SupplierStore store = supplierStoreFactory.getStore(event);
        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            List<Supplier> suppliers = store.getSuppliers(event);
            List<OrganizationReference1> awardedSuppliers = suppliers.stream().map(this::convertSuppliers).toList();
            award.setSuppliers(awardedSuppliers);
        });
        return new MapperResponse(re);
    }

    private OrganizationReference1 convertSuppliers(Supplier supplier) {
        OrganizationReference1 ref = new OrganizationReference1();
        ref.setName(supplier.getId());
        ref.setName(supplier.getCompanyData().getName());
        return ref;
    }

    public MapperResponse populateItems(Record1 re, ProjectQuery pq) {
        log.warn("populating Items not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateDocuments(Record1 re, ProjectQuery pq) {
        log.debug("populating Documents");
        return new MapperResponse(re);
    }

    public MapperResponse populateAmendments(Record1 re, ProjectQuery pq) {
        log.warn("populating Amendments not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateRequiremetResponses(Record1 re, ProjectQuery pq) {
        log.warn("populating Enquiries not yet implemented");
        return new MapperResponse(re);
    }

    public Award2 getAward(Record1 re){
        List<Award2> awards = OcdsHelper.getAwards(re);
        if(awards.size() > 0)
            return awards.get(0);
        else{
            Award2 award = new Award2();
            awards.add(award);
            return award;
        }
    }
}
