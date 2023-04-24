package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ScriptConfig;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierModel;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierLinkReader;
import uk.gov.crowncommercial.dts.scale.cat.mapper.SupplierLinkMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierLinkRepo;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierLinkUploader {

    private final ScriptConfig scriptConfig;
    private final SupplierLinkMapper mapper;
    private final SupplierLinkRepo supplierLinkRepo;

    private Map<String, JaggaerSupplierModel> jaggaerSupplierMap;

    public void upload(){
        Path supplierLinkPath = Paths.get(scriptConfig.getDataFolder(), "suppliers", "supplier_link.csv");

        File supplierLinkFile = supplierLinkPath.toFile();
        if(!supplierLinkFile.exists()){
            log.trace("supplierLink csv file does not exist");
            return;
        }

        SupplierLinkReader reader = new SupplierLinkReader();
        reader.parallelRecordProcess(supplierLinkFile, new Consumer<SupplierLink>() {
            @Override
            public void accept(SupplierLink supplierLink) {
                try {
                    mapper.formatIds(supplierLink);
                    populateBravoId(supplierLink);
//                    Example<SupplierLinkEntity> example = Example.of(mapper.getExample(supplierLink), ExampleMatcher.matchingAny());
//                    List<SupplierLinkEntity> entityList = supplierLinkRepo.findAll(example);
//                    if (0 == entityList.size()) {
//                        supplierLinkRepo.save(mapper.toEntity(supplierLink));
//                    } else if (1 == entityList.size()) {
//                        supplierLinkRepo.save(mapper.toEntity(supplierLink, entityList.get(0)));
//                    } else {
//                        log.error("Multiple entries present for the supplier Link ");
//                    }
                }catch(Throwable t){
                    t.printStackTrace();
                    throw t;
                }
            }
        }, 4);
    }

    private void populateBravoId(SupplierLink supplierLink) {
        if(null == supplierLink.getBravoId() && null != jaggaerSupplierMap){
            JaggaerSupplierModel model = getJaggaerSupplierModel(supplierLink.getDunsNumber(), supplierLink.getCohNumber());
            if(null != model){
                try {
                    supplierLink.setBravoId(Integer.parseInt(model.getBravoId()));
                }catch(Throwable t){}
            }
        }
    }

    private JaggaerSupplierModel getJaggaerSupplierModel(String dunsNumber, String cohNumber) {
        JaggaerSupplierModel model = queryCompanyFromJaggaer(dunsNumber);
        if(null == model && null != cohNumber){
            model = queryCompanyFromJaggaer(cohNumber);
        }
        if(null == model){
            model = queryCompanyJaggaerWithTrim(dunsNumber);
        }
        if(null == model  && null != cohNumber){
            model = queryCompanyJaggaerWithTrim(cohNumber);
            if(null == model){
                model = queryCompanyByFiscalCoh(cohNumber);
                if(null != model){
                    System.out.println("\""+ model.getSupplierName() +"\"," + model.getBravoId() + "," + dunsNumber + "," + model.getExtUniqueCode() + "," + model.getExtCode() + ",\"" + model.getFiscalCode() +"\"");
                }
            }
        }

        return model;
    }

    private JaggaerSupplierModel queryCompanyByFiscalCoh(String coh) {
        for(JaggaerSupplierModel model : jaggaerSupplierMap.values()){
            if(null != model.getFiscalCode()){
                if(coh.equals(Util.getCoHId(model.getFiscalCode())))
                    return model;
            }
        }
        return null;
    }

    private JaggaerSupplierModel queryCompanyFromJaggaer(String duns) {
        for(JaggaerSupplierModel model : jaggaerSupplierMap.values()){
            if(null != model.getExtUniqueCode() && duns.equalsIgnoreCase(model.getExtUniqueCode())){
                return model;
            }
        }
        return null;
    }

    private JaggaerSupplierModel queryCompanyJaggaerWithTrim(String duns) {
        for(JaggaerSupplierModel model : jaggaerSupplierMap.values()){
            if(null != model.getExtUniqueCode()){
                String supplierDuns = model.getExtUniqueCode().replaceAll("-", "");
                supplierDuns = supplierDuns.replaceAll(" ", "");
                if(duns.equalsIgnoreCase(supplierDuns))
                    return model;
            }
        }
        return null;
    }

    public void setJaggaerSupplierMap(Map<String, JaggaerSupplierModel> jaggaerSupplierMap){
        this.jaggaerSupplierMap = jaggaerSupplierMap;
    }
}
