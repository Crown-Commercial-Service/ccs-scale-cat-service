package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Award2;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseAwardsService extends AbstractOcdsService{

    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        ProcurementProject pp = pq.getProject();

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ExportRfxResponse rfxResponse = getLatestRFXWithSuppliers(pq);
            RfxSetting rfxSetting = rfxResponse.getRfxSetting();

            if(500 == rfxResponse.getRfxSetting().getStatusCode()){
                List<Supplier> awardedSuppliers = rfxResponse.getSuppliersList().getSupplier().stream().filter(f -> 3 == f.getStatusCode()).toList();

                Award2 award = getAward(re);
                award.setTitle(pp.getProjectName());
                award.setSuppliers(awardedSuppliers.stream().map(this::convertSuppliers).toList());
                award.setDescription(null);
                award.setDate(rfxSetting.getAwardDate());
                award.setValue(null);
            }
        });
        return new MapperResponse(re, cf);
    }

    private OrganizationReference1 convertSuppliers(Supplier supplier) {
        OrganizationReference1 ref = new OrganizationReference1();
        if(null != supplier.getCompanyData()) {
            CompanyData cData = supplier.getCompanyData();
            ref.setId(cData.getCode());
            ref.setName(cData.getName());
        }
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
