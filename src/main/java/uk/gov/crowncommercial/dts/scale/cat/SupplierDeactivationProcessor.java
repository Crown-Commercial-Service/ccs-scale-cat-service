package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.agreement.model.dto.SupplierDetails;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierModel;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierDetailsWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.repo.OrganisationMappingRepo;

import java.util.Map;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
public class SupplierDeactivationProcessor implements BiConsumer<SupplierDetails, Integer> {
    private final OrganisationMappingRepo orgMapRepo;
    private final Map<String, JaggaerSupplierModel> jaggaerSupplierMap;
    private final String logLocation;
    private SupplierDetailsWriter writer;

    @Override
    public void accept(SupplierDetails supplier, Integer bravoId) {
        if(!jaggaerSupplierMap.containsKey(bravoId)){
            deactivateOrgMapping(supplier, bravoId);
        }
    }

    private void deactivateOrgMapping(SupplierDetails supplier, Integer bravoId) {
        OrganisationMapping om = orgMapRepo.findByExternalOrganisationId(bravoId).orElse(null);
        if(null != om){
            SupplierDetailsWriter writer = getWriter();
            om.setPrimaryInd(false);
            writer.accept(supplier);
            orgMapRepo.save(om);
        }
    }

    private SupplierDetailsWriter getWriter() {
        if(null ==  writer) {
            writer = new SupplierDetailsWriter(logLocation, "deactivated_suppliers.csv");
            writer.initHeader();
        }
        return writer;
    }

    public void complete(){
        writer.complete();
    }
}
