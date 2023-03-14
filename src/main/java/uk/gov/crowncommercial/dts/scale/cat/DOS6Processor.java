package uk.gov.crowncommercial.dts.scale.cat;


import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import uk.gov.crowncommercial.dts.scale.agreement.model.dto.SupplierDetails;
import uk.gov.crowncommercial.dts.scale.cat.config.ScriptConfig;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.*;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.JaggaerSupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierDetailsWriter;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierSuggestionWriter;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerCompanyService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@SpringBootApplication
@RequiredArgsConstructor
@EnableCaching
@Log4j2
public class DOS6Processor implements CommandLineRunner {

    private final JaggaerCompanyService companyService;
    private final JaggaerSupplierDownloader supplierDownloader;

    private final AgreementService supplierService;

    private final ScriptConfig scriptConfig;
    //    private final JaggaerQuerySync querySyncConsumer;
    private Map<String, CiiSingleOrg> ciiOrgSingleMap;

    private JaggaerMatcher jaggaerMatcher;
    Map<String, CiiOrg> ciiOrgMap;

    Map<String, JaggaerSupplierModel> jaggaerSupplierMap;
    private static final String BUSINESS_INPUT_FILE = "DOS6_suppliers";

    private static final String AGREEMENT_DATA_FILE = "dos6_all";
    private static final String AGREEMENT_DATA_LOT = "dos6_lot";
    private SupplierSuggestionWriter suggestionsWriter;

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

    private String getBaseFolder(){
        return scriptConfig.getBaseFolder();
    }

    private String getCurrentFolder(){
        return scriptConfig.getCurrentFolder();
    }

    public void run(String... args) throws Exception {

        initFolder(getCurrentFolder());

        fetchSuppliers(AGREEMENT_DATA_FILE);
        String currentFolder = getCurrentFolder();
        supplierDownloader.downloadSuppliers(currentFolder, "input/jaggaer_suppliers.csv");
        jaggaerSupplierMap = supplierDownloader.getSuppliers(currentFolder, "input/jaggaer_suppliers.csv");
        jaggaerMatcher = new JaggaerMatcher(jaggaerSupplierMap);
        jaggaerMatcher.init();
//        writeDuplicateJaggaerSuppliers(AGREEMENT_DATA_FILE, BUSINESS_INPUT_FILE);

        advancedLocalJaggaerDataSync(AGREEMENT_DATA_FILE, BUSINESS_INPUT_FILE);
        generateLotWiseReport();

        System.exit(0);
    }

    @SneakyThrows
    private void initFolder(String currentFolder) {
        Files.createDirectories(Paths.get(currentFolder));
    }

    private void fetchSuppliers(String agreeementDataFileName) {
        writeToFile(supplierService.getSuppliers(), "input/" + agreeementDataFileName + ".csv");
        writeToFile(supplierService.getSuppliers("1"), "input/" + AGREEMENT_DATA_LOT + "1.csv");
        writeToFile(supplierService.getSuppliers("2"), "input/" + AGREEMENT_DATA_LOT + "2.csv");
        writeToFile(supplierService.getSuppliers("3"), "input/" + AGREEMENT_DATA_LOT + "3.csv");
    }

    private void writeToFile(List<SupplierDetails> suppliers, String filename) {
        SupplierDetailsWriter detailsWriter = new SupplierDetailsWriter(getCurrentFolder(), filename);
        detailsWriter.initHeader();
        suppliers.stream().forEach(detailsWriter);
        detailsWriter.complete();
    }

    private void writeDuplicateJaggaerSuppliers(String agreeementDataFileName, String businessInputFileName) {
        SupplierCSVReader reader = new SupplierCSVReader();
        File businessInputFile = Paths.get(getBaseFolder(), "input", businessInputFileName + ".csv").toFile();
        File agreementDataFile = Paths.get(getCurrentFolder(), "input", agreeementDataFileName + ".csv").toFile();
        String currentFolder = getCurrentFolder();
        List<SupplierModel> supplierList = new ArrayList<>();
        List<OrganizationModel> orgList = new ArrayList<>();
        reader.parallelRecordProcess(businessInputFile, new Consumer<SupplierModel>(){
            @Override
            public void accept(SupplierModel supplierModel) {
                supplierList.add(supplierModel);
            }
        });

        AbstractCSVReader<OrganizationModel> orgReader = new OrganisationCSVReader();
        Map<String, OrganizationModel> orgMap = new HashMap<>();

        orgReader.processFile(agreementDataFile, new Consumer<OrganizationModel>() {
            @Override
            public void accept(OrganizationModel organizationModel) {
                orgList.add(organizationModel);
            }
        });

        JaggaerSupplierWriter writer = new JaggaerSupplierWriter(currentFolder, "dos6_duplicate_suppliers.csv");
        writer.initHeader();



        Map<String, List<JaggaerSupplierModel>> duplicateSupplierMap = jaggaerMatcher.getDuplicateByDomain(orgList);
        for(List<JaggaerSupplierModel> suppliers: duplicateSupplierMap.values()){
            for(JaggaerSupplierModel supplier: suppliers)
                writer.accept(supplier);
        }
        writer.complete();
    }

    @SneakyThrows
    private void advancedLocalJaggaerDataSync(String agreeementDataFileName, String businessInputFileName) {
        String currentFolder = getCurrentFolder();
        SupplierCSVReader reader = new SupplierCSVReader();

        ciiOrgMap = getCiiOrgs("cii_orgs");
        ciiOrgSingleMap = getCiiSingleOrgs("cii_single");

        File agreementDataFile = Paths.get(currentFolder, "input", agreeementDataFileName + ".csv").toFile();

        File businessInputFile = Paths.get(getBaseFolder(), "input", businessInputFileName + ".csv").toFile();

        File errorFile = Paths.get(currentFolder, businessInputFileName + "_error.txt").toFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(errorFile));

        Map<String, Integer> orgMappings = new HashMap<>();
        for(OrganisationMapping om : service.listAll()){
            String key = Util.getEntityId(om.getCasOrganisationId());
            if(om.isPrimaryInd())
                orgMappings.put(key, om.getExternalOrganisationId());
        }

//        Map<String, Integer> orgMappings = service.listAll().stream()
//                .collect(Collectors.toMap(OrganisationMapping::getOrganisationId, OrganisationMapping::getExternalOrganisationId));

        SupplierWriter writer = new SupplierWriter(currentFolder, businessInputFileName + "_completed.csv");
        SupplierWriter missingWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing.csv");
        SupplierWriter missingJaggaerWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing_jaggaer.csv");
        SupplierWriter missingCASWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing_cas.csv");
        suggestionsWriter = new SupplierSuggestionWriter(currentFolder, businessInputFileName + "_suggestions.csv");

        writer.initHeader();
        missingWriter.initHeader();
        missingJaggaerWriter.initHeader();
        missingCASWriter.initHeader();
        suggestionsWriter.initHeader();

        AbstractCSVReader<OrganizationModel> orgReader = new OrganisationCSVReader();
        Map<String, OrganizationModel> orgMap = new HashMap<>();

        orgReader.processFile(agreementDataFile, new Consumer<OrganizationModel>() {
            @Override
            public void accept(OrganizationModel organizationModel) {
                orgMap.put(Util.getEntityId(organizationModel.getEntityId()), organizationModel);
            }
        });


        SupplierProcessor processor = new SupplierProcessor(scriptConfig, agreeementDataFileName, businessInputFileName,
                ciiOrgMap, ciiOrgSingleMap, jaggaerSupplierMap, orgMappings, orgMap, service);

        processor.initWriters();

        reader.parallelRecordProcess(businessInputFile, processor);

        Thread.sleep(15 * 1000);
        processor.close();
        Thread.sleep(10 * 1000);
    }

    private ReturnCompanyData getCompanyData(SupplierModel supplierModel, String duns) {
        ReturnCompanyData data = queryCompanyFromJaggaer(duns);
        String houseNUmber = getHouseNumber(supplierModel.getHouseNumber());
        if (null == data && null != supplierModel.getHouseNumber()) {
            if (null != houseNUmber) {
                data = queryCompanyFromJaggaer(houseNUmber);
                if (null == data && houseNUmber.length() == 7) {
                    data = queryCompanyFromJaggaer("0" + houseNUmber);
                }
            }
            if (null == data) {
                String dunsNumber = "US-DUNS-" + duns;
                data = queryCompanyFromJaggaer(dunsNumber);
                if (null != data) {
                    supplierModel.setMapping("DUNS", dunsNumber);
                } else {
                    String coh = getCoH(supplierModel);
                    if (null != coh) {
                        if (!coh.equalsIgnoreCase(houseNUmber)) {
                            data = queryCompanyFromJaggaer(coh);
                            if (null != data) {
                                supplierModel.setMapping("COH", coh);
                            }
                        }
                    }
                }
            } else {
                supplierModel.setMapping("COH", supplierModel.getHouseNumber());
            }
        } else {
            supplierModel.setMapping("DUNS", duns);
        }

        if (null == data) {
            if (null != houseNUmber && houseNUmber.length() < 9) {
                String gbCoh = "GB-COH-";
                if (houseNUmber.length() == 7)
                    gbCoh += "0" + houseNUmber;
                else
                    gbCoh = houseNUmber;

                if (null != data) {
                    supplierModel.setMapping("CoH", gbCoh);
                    return data;
                }
            }
//
//            String dunsNumber = "US-DUN-" + duns;
//            data = queryCompanyByExtUniqueCode(duns);
//            if(null != data){
//                supplierModel.setMapping("DUNS", dunsNumber);
//                return data;
//            }
        }

        if (null == data) {
            data = getByJaggaerSupplier(supplierModel);
//            if (null != data) {
//                supplierModel.setFuzzyMatch("true");
//                supplierModel.setMappingType("JaggaerBravo");
//            }

        }

        return data;
    }

    private Double getSimilarity(SupplierModel supplierModel) {
        String source = supplierModel.getSupplierName();

        String target = null != supplierModel.getJaggaerSupplierName() ? supplierModel.getJaggaerSupplierName() : supplierModel.getLegalName();

        return Util.getSimilarity(source, target);
    }

    private String getCoH(SupplierModel supplierModel) {

        String houseNumber = supplierModel.getHouseNumber();

        String duns = Util.getEntityId(supplierModel.getEntityId());

        if (ciiOrgMap.containsKey(duns)) {
            CiiOrg org = ciiOrgMap.get(duns);
            String coh = org.getCoH();
            if (null != coh) {
                if (Util.isCohEqual(houseNumber, coh)) return null;
                supplierModel.setCiiCoH(coh);
                supplierModel.setCiiOrgName(org.getOrgName());
                supplierModel.setFuzzyMatch("no");
                return coh;
            }
        }


        String currentMappedOrgName = null;
        String currentMappedCoH = null;
        String target = "";
        Double currentSimilarity = 0d;
        for (CiiSingleOrg org : ciiOrgSingleMap.values()) {
            target = null != supplierModel.getSupplierName() ? supplierModel.getSupplierName() : supplierModel.getLegalName();
            String organisation = org.getOrgName();

            Double similarity = Util.getSimilarity(target, organisation);
            if (similarity > currentSimilarity) {
                currentSimilarity = similarity;
                currentMappedCoH = org.getCoH();
                currentMappedOrgName = organisation;
                if (1d == currentSimilarity) {
                    break;
                }
            }
        }
        if (currentSimilarity > 0.85) {
            if (Util.isCohEqual(houseNumber, currentMappedCoH)) return null;
            supplierModel.setCiiCoH(currentMappedCoH);
            supplierModel.setCiiOrgName(currentMappedOrgName);
            supplierModel.setFuzzyMatch("yes");
            log.trace("supplier '" + target + "' is matched with " + currentMappedOrgName + " max similarity " + currentSimilarity);
            return currentMappedCoH;
        }
//        if(currentSimilarity != 0d){
//            System.out.println("---------------------------- supplier '" + target + "' is not matched with " + currentMappedOrgName + " max similarity " + currentSimilarity);
//        }
        return null;
    }

    private ReturnCompanyData getByJaggaerSupplier(SupplierModel supplierModel) {
        JaggaerSupplierModel jaggaerSupplierModel = jaggaerMatcher.getMatchingSupplier(supplierModel);
        if (null != jaggaerSupplierModel) {
            if(null == supplierModel.getFuzzyMatch()){
                supplierModel.setFuzzyMatch("yes");
                supplierModel.setMappingType("JaggaerBravo");
            }
            return companyService.getSupplierDataByBravoId(jaggaerSupplierModel.getBravoId()).orElse(null);
        }
        return null;
    }

    private String getHouseNumber(String houseNumber) {
        if (null == houseNumber)
            return houseNumber;
        String value = houseNumber.trim();
        if (value.contains(",")) {
            String[] split = value.split(",");
            return split[0];
        } else if (value.contains(" ")) {
            String[] split = value.split(" ");
            return split[0];
        }
//        if(value.length() == 7)
//            return "0" + value;
        return value;
    }

    @SneakyThrows
    public void generateLotWiseReport() {
        String baseFolder = scriptConfig.getBaseFolder();
        String baseXlsFolder = scriptConfig.getCurrentFolder();

        LotWiseReporter reporter = new LotWiseReporter();
        reporter.GenerateReport(baseFolder,baseXlsFolder, "DOS6_suppliers", "dos6_lot1");
        reporter.GenerateReport(baseFolder,baseXlsFolder, "DOS6_suppliers", "dos6_lot2");
        reporter.GenerateReport(baseFolder, baseXlsFolder,"DOS6_suppliers", "dos6_lot3");



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
        try {
            Optional<ReturnCompanyData> optCompany = companyService.getSupplierDataByDUNSNumber(dunsNumber);
            if (optCompany.isPresent()) {
                return optCompany.get();
            } else {
                return null;
            }
        } catch (Throwable t) {
            System.out.println("error while querying jaggaer for " + dunsNumber);
            return null;
        }
    }
//
//    public ReturnCompanyData queryCompanyByExtUniqueCode(String uniqueCode) {
//        try {
//            Optional<ReturnCompanyData> optCompany = companyService.getSupplierDataByExtCode(uniqueCode);
//            if (optCompany.isPresent()) {
//                return optCompany.get();
//            } else {
//                return null;
//            }
//        }catch(Throwable t){
//            System.out.println("error while querying jaggaer for using uniqueCode" + uniqueCode);
//            return null;
//        }
//    }

    private Map<String, CiiOrg> getCiiOrgs(String filename) {
        CiiOrgReader reader = new CiiOrgReader();
        File ciiOrgFile = Paths.get(scriptConfig.getBaseFolder(), "input", filename + ".csv").toFile();
        Map<String, CiiOrg> result = new HashMap<>();
        reader.processFile(ciiOrgFile, new Consumer<CiiOrg>() {
            @Override
            public void accept(CiiOrg ciiOrg) {
                String dunsNumber = Util.getEntityId(ciiOrg.getDuns());
                if (null != dunsNumber) {
                    result.put(dunsNumber, ciiOrg);
                }
            }
        });
        return result;
    }


    private Map<String, CiiSingleOrg> getCiiSingleOrgs(String filename) {
        CiiSingleOrgReader reader = new CiiSingleOrgReader();
        File ciiOrgFile = Paths.get(getBaseFolder(), "input", filename + ".csv").toFile();
        Map<String, CiiSingleOrg> result = new HashMap<>();
        reader.processFile(ciiOrgFile, new Consumer<CiiSingleOrg>() {
            @Override
            public void accept(CiiSingleOrg ciiOrg) {
                String Coh = ciiOrg.getCoH();
                if (null != Coh) {
                    result.put(Coh, ciiOrg);
                }else if(null != ciiOrg.getDuns()){
                    result.put(ciiOrg.getDuns(), ciiOrg);
                }
            }
        });
        return result;
    }
}
