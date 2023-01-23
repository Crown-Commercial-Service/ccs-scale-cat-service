package uk.gov.crowncommercial.dts.scale.cat;


import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.text.similarity.JaccardDistance;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.*;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerCompanyService;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


//@SpringBootApplication
//@EnableJpaRepositories("uk.gov.crowncommercial.dts.scale.cat.*")
@RequiredArgsConstructor
public class CompileCSVData {

    private static final String SUPPLIER_DATA_FILE = "/home/ksvraja/supplier_dos6_prod.txt";

    private static final String BASE_FOLDER = "/home/ksvraja/brickendon/org_sync";
    private static final String AGREEMENT = "DOS6_suppliers";
    private JaroWinklerDistance winklerDistance = new JaroWinklerDistance();
    private JaccardDistance jaccardDistance = new JaccardDistance();


    private List<String> emailList = Arrays.asList("tenders@concerto.uk.com",
            "contracts@curzonconsulting.com",
            "tenders@deardenhr.co.uk",
            "shirley.dalziel@develop-global.com",
            "info@egremontgroup.com",
            "supplier@gateoneconsulting.com",
            "ccs.gel@guidehouse.com",
            "ukcat@uk.ibm.com",
            "tenders@ignite.org.uk",
            "stuart.pearce@journey4.co.uk");

    private Map<String, String> supplierMap = new HashMap<>();

    private final SaveService service;

    private final EmailTranslator emailTranslator;

    @SneakyThrows
    private void compileCSV(String agreementName) {
        SupplierCSVReader reader = new SupplierCSVReader();

        File file = Paths.get(BASE_FOLDER, agreementName + ".csv").toFile();
        File errorFile = Paths.get(BASE_FOLDER, agreementName + "_error.txt").toFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(errorFile));

        File orgFile = Paths.get(BASE_FOLDER, "dos6org.csv").toFile();

        SupplierWriter writer = new SupplierWriter(BASE_FOLDER, agreementName + "_merged.csv");
//        SupplierWriter missingWriter = new SupplierWriter(BASE_FOLDER, agreementName + "_missing.csv");
        writer.initHeader();
//        missingWriter.initHeader();

        AbstractCSVReader<OrganizationModel> orgReader = new OrganisationCSVReader();
        Map<String,OrganizationModel> orgMap = new HashMap<>();

        orgReader.processFile(orgFile, new Consumer<OrganizationModel>() {
            @Override
            public void accept(OrganizationModel organizationModel) {
                orgMap.put(organizationModel.getEntityId(), organizationModel);
            }
        });

        reader.processFile(file, new Consumer<SupplierModel>() {
            @Override
            public void accept(SupplierModel supplierModel) {
                String duns = supplierModel.getEntityId();
                OrganizationModel model = orgMap.get(duns);
                if(null != model){
                    supplierModel.setLegalName(model.getLegalName());
                    supplierModel.setTradingName(model.getTradingName());
                }
                writer.accept(supplierModel);
            }
        });

        bw.close();
        writer.complete();
//        missingWriter.complete();
    }

    private Double getSimilarity(SupplierModel supplierModel) {
        if(null == supplierModel.getSupplierName() || null == supplierModel.getJaggaerSupplierName())
            return 0d;
        String supplierName = supplierModel.getSupplierName().toLowerCase().replaceAll("limited", "").replace("ltd", "").trim();
        String jaggerName = supplierModel.getJaggaerSupplierName().toLowerCase().replaceAll("limited", "").replace("ltd", "").trim();
        if(supplierName.equalsIgnoreCase(jaggerName))
            return 1d;
        return winklerDistance.apply(supplierName, jaggerName);
    }

    private String getHouseNumber(String houseNumber){
        String value = houseNumber.trim();
        if(value.contains(",")){
            String[] split = value.split(",");
            return split[0];
        }else if(value.contains(" ")){
            String[] split = value.split(" ");
            return split[0];
        }
        return value;
    }
}
