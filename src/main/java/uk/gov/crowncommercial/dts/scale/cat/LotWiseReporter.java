package uk.gov.crowncommercial.dts.scale.cat;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.*;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierWriter;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class LotWiseReporter {

    public void lotwiseReport(String baseFolder, String agreement){

    }

    public void GenerateReport(String baseFolder, String currentFolder, String agreement, String lotFile){
//        File agreementDataFile = Paths.get(baseFolder, "input", agreement + ".csv").toFile();
        File lotDataFile = Paths.get(currentFolder, "input",lotFile + ".csv").toFile();

        AbstractCSVReader<OrganizationModel> orgReader = new OrganisationCSVReader();
        Map<String, OrganizationModel> orgMap = new HashMap<>();

        orgReader.processFile(lotDataFile, new Consumer<OrganizationModel>() {
            @Override
            public void accept(OrganizationModel organizationModel) {
                orgMap.put(Util.getEntityId(organizationModel.getEntityId()), organizationModel);
            }
        });


        doReport(baseFolder, currentFolder, agreement, "completed", lotFile, orgMap);
        doReport(baseFolder, currentFolder,agreement, "missing", lotFile, orgMap);
        doReport(baseFolder, currentFolder,agreement, "missing_jaggaer", lotFile, orgMap);
    }

    private void doReport(String baseFolder, String currentFolder, String fileName, String action,  String lotFileName, Map<String, OrganizationModel> lotOrganisations) {
        File dataFile = Paths.get(currentFolder, fileName + "_" + action + ".csv").toFile();
        SupplierWriter writer = new SupplierWriter(currentFolder + lotFileName,  fileName + "_" + lotFileName + "_" + action + ".csv");

        writer.initHeader();
        AbstractCSVReader<SupplierModel> supplierReader = new SupplierCSVReader();
        supplierReader.processFile(dataFile, new Consumer<SupplierModel>() {
            @Override
            public void accept(SupplierModel supplierModel) {
                OrganizationModel org = lotOrganisations.get(Util.getEntityId(supplierModel.getEntityId()));
                if(null != org){
                    if(null != org.getTradingName()){
                        supplierModel.setTradingName(org.getTradingName());
                    }
                    writer.accept(supplierModel);
                }
            }
        });
        writer.complete();
    }
}
