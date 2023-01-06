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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerCompanyService;

import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@SpringBootApplication
//@EnableJpaRepositories("uk.gov.crowncommercial.dts.scale.cat.*")
@RequiredArgsConstructor
public class DOS6Processor implements CommandLineRunner {

    private final JaggaerCompanyService companyService;
//    private final JaggaerQuerySync querySyncConsumer;


    private static final String BASE_FOLDER = "/home/ksvraja/brickendon/org_sync/230103/";
    private static final String BUSINESS_INPUT_FILE = "DOS6_suppliers";

    private static final String AGREEMENT_DATA_FILE = "dos6_all";

    private JaroWinklerDistance winklerDistance = new JaroWinklerDistance();


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

    public static void main(String[] args) {
        SpringApplication.run(DOS6Processor.class, args);
    }

    private final SaveService service;

    private final EmailTranslator emailTranslator;

    @Override

    public void run(String... args) throws Exception {
//        ReturnCompanyData companyData = queryCompanyFromJaggaer("US-DUNS-216961173");

//        advancedSync(AGREEMENT_DATA_FILE, BUSINESS_INPUT_FILE);

        generateLotWiseReport(BASE_FOLDER);

        System.exit(0);
    }


//
//    private void updateSimilarity(String agreementName) {
//        SupplierCSVReader reader = new SupplierCSVReader();
//        File file = Paths.get(BASE_FOLDER, agreementName + "_completed.csv").toFile();
//        SupplierWriter writer = new SupplierWriter(BASE_FOLDER, agreementName + "_calculated.csv");
//        writer.initHeader();
//        reader.processFile(file, new Consumer<SupplierModel>() {
//            @Override
//            public void accept(SupplierModel supplierModel) {
//                supplierModel.setSimilarity(getSimilarity(supplierModel));
//                writer.accept(supplierModel);
//            }
//        });
//        writer.complete();
//    }

    @SneakyThrows
    private void advancedSync(String agreeementDataFileName, String businessInputFileName) {
        SupplierCSVReader reader = new SupplierCSVReader();

        File agreementDataFile = Paths.get(BASE_FOLDER,  "input", agreeementDataFileName + ".csv").toFile();

        File businessInputFile = Paths.get(BASE_FOLDER, "input", businessInputFileName + ".csv").toFile();

        File errorFile = Paths.get(BASE_FOLDER, businessInputFileName + "_error.txt").toFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(errorFile));

        Map<String, Integer> orgMappings = service.listAll().stream()
                .collect(Collectors.toMap(OrganisationMapping::getOrganisationId, OrganisationMapping::getExternalOrganisationId));

        SupplierWriter writer = new SupplierWriter(BASE_FOLDER, businessInputFileName + "_completed.csv");
        SupplierWriter missingWriter = new SupplierWriter(BASE_FOLDER, businessInputFileName + "_missing.csv");
        SupplierWriter missingJaggaerWriter = new SupplierWriter(BASE_FOLDER, businessInputFileName + "_missing_jaggaer.csv");
        SupplierWriter missingCASWriter = new SupplierWriter(BASE_FOLDER, businessInputFileName + "_missing_cas.csv");

        writer.initHeader();
        missingWriter.initHeader();
        missingJaggaerWriter.initHeader();
        missingCASWriter.initHeader();

        AbstractCSVReader<OrganizationModel> orgReader = new OrganisationCSVReader();
        Map<String, OrganizationModel> orgMap = new HashMap<>();

        orgReader.processFile(agreementDataFile, new Consumer<OrganizationModel>() {
            @Override
            public void accept(OrganizationModel organizationModel) {
                orgMap.put(Util.getEntityId(organizationModel.getEntityId()), organizationModel);
            }
        });

        reader.parallelRecordProcess(businessInputFile, new Consumer<SupplierModel>() {
            @Override
            public void accept(SupplierModel supplierModel) {
                String duns = supplierModel.getEntityId();
                if (null == duns) {
                    try {
                        bw.write("Invalid DUNS number for supplier " + supplierModel.getSupplierName());
                        bw.newLine();
                        bw.flush();
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                duns = Util.getEntityId(duns);

//                if(orgMappings.containsKey(dunsNumber)) {
//                    supplierModel.setEntityId(orgMappings.get(dunsNumber) + "");
//                    writer.accept(supplierModel);
//                    return;
//                }

                try {
                    ReturnCompanyData data = getCompanyData(supplierModel, duns);
                    if (null != data) {
                        supplierModel.setJaggaerSupplierName(data.getReturnCompanyInfo().getCompanyName());
                        supplierModel.setBravoId(data.getReturnCompanyInfo().getBravoId());
                        supplierModel.setSimilarity(getSimilarity(supplierModel));


                        if (!orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId()))) {
                            missingCASWriter.accept(supplierModel);
                            return;
                        } else {
                            OrganizationModel model = orgMap.get(Util.getEntityId(supplierModel.getEntityId()));
                            supplierModel.setTradingName(model.getTradingName());
                            supplierModel.setLegalName(model.getLegalName());
                        }

                        String dunsNumber = "US-DUNS-" + duns;
                        String dunNumber = "US-DUN-" + duns;
                        if (orgMappings.containsKey(dunsNumber) || orgMappings.containsKey(dunNumber)) {
                            writer.accept(supplierModel);
                            return;
                        }

                        OrganisationMapping om = service.query(duns);

                        if (null == om) {
                            try {
                                // service.save(duns, data);
                                writer.accept(supplierModel);
                            } catch (Throwable t) {
                                bw.write("cas error:" + t.getMessage());
                                bw.newLine();
                                bw.write(duns + "/" + supplierModel.getSupplierName());
                                if (null != om)
                                    bw.write(" already assigned to " + om.getExternalOrganisationId() + " and");
                                bw.write(" new assignment to " + data.getReturnCompanyInfo().getBravoId() + "/" + data.getReturnCompanyInfo().getCompanyName());
                                bw.newLine();
                                bw.flush();
                            }
                        } else {
                            if (om.getExternalOrganisationId() != Integer.parseInt(data.getReturnCompanyInfo().getBravoId())) {
                                bw.write("cas error:");
                                bw.write(duns + "/" + supplierModel.getSupplierName());
                                bw.write(" already assigned to " + om.getExternalOrganisationId());
                                bw.write(" and cannot be changed to " + data.getReturnCompanyInfo().getBravoId() + "/" + data.getReturnCompanyInfo().getCompanyName());
                                bw.newLine();
                                bw.flush();
                            } else {
                                writer.accept(supplierModel);
                            }
                        }

                    } else {
                        if (orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId()))) {
                            OrganizationModel model = orgMap.get(Util.getEntityId(supplierModel.getEntityId()));
                            supplierModel.setTradingName(model.getTradingName());
                            supplierModel.setLegalName(model.getLegalName());
                            missingJaggaerWriter.accept(supplierModel);
                        } else
                            missingWriter.accept(supplierModel);
                    }
                } catch (Throwable e) {
                    System.out.println("Error while processing duns number " + duns + " " + e.getMessage());
                    try {
                        bw.write("cas error:");
                        if (null == duns)
                            bw.write("empty duns");
                        else
                            bw.write(duns);
                        bw.write(e.getMessage());
                        bw.newLine();
                        bw.flush();
                    } catch (IOException ex) {
                    }
                }
            }
        });

        Thread.sleep(60 * 1000);

        bw.close();
        writer.complete();
        missingCASWriter.complete();
        missingJaggaerWriter.complete();
        missingWriter.complete();
        Thread.sleep(30*1000);
    }

    private ReturnCompanyData getCompanyData(SupplierModel supplierModel, String duns) {
        ReturnCompanyData data = queryCompanyFromJaggaer(duns);
        if (null == data && null != supplierModel.getHouseNumber()) {
            data = queryCompanyFromJaggaer(getHouseNumber(supplierModel.getHouseNumber()));
            if (null == data) {
                String dunsNumber = "US-DUNS-" + duns;
                data = queryCompanyFromJaggaer(dunsNumber);
            }
        }
        return data;
    }

    private Double getSimilarity(SupplierModel supplierModel) {
        String source = supplierModel.getSupplierName();

        String target= null != supplierModel.getJaggaerSupplierName() ? supplierModel.getJaggaerSupplierName() : supplierModel.getLegalName();

        if(null == target || null ==  source)
            return 0d;

        String sourceName = source.toLowerCase().replaceAll("limited", "").replace("ltd", "").trim();
        String targetName = target.toLowerCase().replaceAll("limited", "").replace("ltd", "").trim();
        if (sourceName.equalsIgnoreCase(targetName))
            return 1d;
        return winklerDistance.apply(sourceName, targetName);
    }

    private String getHouseNumber(String houseNumber) {
        String value = houseNumber.trim();
        if (value.contains(",")) {
            String[] split = value.split(",");
            return split[0];
        } else if (value.contains(" ")) {
            String[] split = value.split(" ");
            return split[0];
        }
        return value;
    }

    @SneakyThrows
    public void generateLotWiseReport(String baseFolder){
        LotWiseReporter reporter = new LotWiseReporter();
        reporter.GenerateReport(baseFolder, "DOS6_suppliers", "dos6_lot1");
        reporter.GenerateReport(baseFolder, "DOS6_suppliers", "dos6_lot2");
        reporter.GenerateReport(baseFolder, "DOS6_suppliers", "dos6_lot3");

        String baseXlsFolder = baseFolder;

        generateXLSSheet(baseXlsFolder);
        generateXLSSheet(baseXlsFolder, "dos6_lot1");
        generateXLSSheet(baseXlsFolder, "dos6_lot2");
        generateXLSSheet(baseXlsFolder, "dos6_lot3");

    }

    private void generateXLSSheet(String baseXlsFolder, String lot) throws IOException {
        SupplierCSVtoXLSConverter converter = new SupplierCSVtoXLSConverter(baseXlsFolder, lot);
        converter.convertToXLS("DOS6_suppliers", lot);
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        converter.writeTo("Report_DOS6_suppliers_" + lot + "_" + format.format(new Date()));
    }

    private void generateXLSSheet(String baseXlsFolder) throws IOException {
        SupplierCSVtoXLSConverter converter = new SupplierCSVtoXLSConverter(baseXlsFolder, null);
        converter.convertToXLS("DOS6_suppliers");
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        converter.writeTo("Report_DOS6_suppliers_" + format.format(new Date()));
    }

    public ReturnCompanyData queryCompanyFromJaggaer(String dunsNumber) {
        Optional<ReturnCompanyData> optCompany = companyService.getSupplierDataByDUNSNumber(dunsNumber);
        if (optCompany.isPresent()) {
            return optCompany.get();
        } else {
            return null;
        }
    }
}
