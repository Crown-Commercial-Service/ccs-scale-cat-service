package uk.gov.crowncommercial.dts.scale.cat;


import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.codec.language.bm.PhoneticEngine;
import org.apache.commons.text.similarity.JaccardDistance;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.*;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
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
import java.util.function.Function;
import java.util.stream.Collectors;


//@SpringBootApplication
//@EnableJpaRepositories("uk.gov.crowncommercial.dts.scale.cat.*")
@RequiredArgsConstructor
public class CliInvoker implements CommandLineRunner {

    private final JaggaerCompanyService companyService;
//    private final JaggaerCreateSync createSyncConsumer;
    private final JaggaerQuerySync querySyncConsumer;


    private static final String SUPPLIER_DATA_FILE = "/home/ksvraja/supplier_dos6_prod.txt";

    private static final String BASE_FOLDER = "/home/ksvraja/brickendon/org_sync";
    private static final String AGREEMENT = "DOS6_suppliers";
    private static final String MISSING_SUPPLIER_FILE = "/home/ksvraja/supplier_dos6_prod_missing.txt";
    private static final String PROCESSED_SUPPLIER_FILE = "/home/ksvraja/supplier_dos6_prod_completed.txt";
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

    public static void main(String[] args) {
        SpringApplication.run(CliInvoker.class, args);
    }

    private final SaveService service;

    private final EmailTranslator emailTranslator;

    @Override

    public void run(String... args) throws Exception {
        ReturnCompanyData companyData = queryCompanyFromJaggaer("US-DUNS-216961173");
        ReturnCompanyData companyDatas = queryCompanyFromJaggaer("216961173");
        // System.out.println(companyData.getReturnCompanyInfo().getBravoId());
//    createInJaggaer(new File("/home/ksvraja/brickendon/org_sync/test_jaggaer/"));

        advancedSync(AGREEMENT);
//        updateSimilarity(AGREEMENT);
//        System.out.println("Completed");
    }


    private void createInJaggaer(File rootDir){
        SupplierContactCSVReader csvReader = new SupplierContactCSVReader();
        csvReader.process(rootDir, querySyncConsumer);
    }

    private void updateSimilarity(String agreementName) {
        SupplierCSVReader reader = new SupplierCSVReader();
        File file = Paths.get(BASE_FOLDER, agreementName + "_completed.csv").toFile();
        SupplierWriter writer = new SupplierWriter(BASE_FOLDER, agreementName + "_calculated.csv");
        writer.initHeader();
        reader.processFile(file, new Consumer<SupplierModel>() {
            @Override
            public void accept(SupplierModel supplierModel) {
                supplierModel.setSimilarity(getSimilarity(supplierModel));
                writer.accept(supplierModel);
            }
        });
        writer.complete();
    }

    @SneakyThrows
    private void advancedSync(String agreementName) {
        SupplierCSVReader reader = new SupplierCSVReader();

        File file = Paths.get(BASE_FOLDER, agreementName + ".csv").toFile();
        File errorFile = Paths.get(BASE_FOLDER, agreementName + "_error.txt").toFile();
        BufferedWriter bw = new BufferedWriter(new FileWriter(errorFile));

        Map<String, Integer> orgMappings = service.listAll().stream()
                .collect(Collectors.toMap(OrganisationMapping::getOrganisationId, OrganisationMapping::getExternalOrganisationId));

        SupplierWriter writer = new SupplierWriter(BASE_FOLDER, agreementName + "_completed.csv");
        SupplierWriter missingWriter = new SupplierWriter(BASE_FOLDER, agreementName + "_missing.csv");
        writer.initHeader();
        missingWriter.initHeader();

        reader.parallelRecordProcess(file, new Consumer<SupplierModel>() {
            @Override
            public void accept(SupplierModel supplierModel) {
                String duns = supplierModel.getEntityId();
                if(null == duns){
                    try {
                        bw.write("Invalid DUNS number for supplier " + supplierModel.getSupplierName());
                        bw.newLine();
                        bw.flush();
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                duns= Util.getEntityId(duns);
                String dunsNumber = "US-DUNS-" + duns;
                if(orgMappings.containsKey(dunsNumber)) {
                    supplierModel.setEntityId(orgMappings.get(dunsNumber) + "");
                    writer.accept(supplierModel);
                    return;
                }

                try {
                    ReturnCompanyData data = queryCompanyFromJaggaer(duns);
                    if(null ==  data && null != supplierModel.getHouseNumber()){
                        data = queryCompanyFromJaggaer(getHouseNumber(supplierModel.getHouseNumber()));
                    }
                    if (null != data) {

                        OrganisationMapping om = service.query(duns);
                        supplierModel.setJaggaerSupplierName(data.getReturnCompanyInfo().getCompanyName());
                        supplierModel.setBravoId(data.getReturnCompanyInfo().getBravoId());
                        supplierModel.setSimilarity(getSimilarity(supplierModel));

                        if(null == om) {
                            try {
                                service.save(duns, data, null);
                                writer.accept(supplierModel);
                            }catch(Throwable t){
                                bw.write("cas error:" + t.getMessage());
                                bw.newLine();
                                bw.write(duns + "/" + supplierModel.getSupplierName());
                                if(null != om)
                                    bw.write(" already assigned to " + om.getExternalOrganisationId() + " and");
                                bw.write(" new assignment to " + data.getReturnCompanyInfo().getBravoId() + "/" + data.getReturnCompanyInfo().getCompanyName());
                                bw.newLine();
                                bw.flush();
                            }
                        }else{
                            if(om.getExternalOrganisationId() != Integer.parseInt(data.getReturnCompanyInfo().getBravoId())){
                                bw.write("cas error:");
                                bw.write(duns + "/" + supplierModel.getSupplierName());
                                bw.write(" already assigned to " + om.getExternalOrganisationId() );
                                bw.write(" and cannot be changed to " + data.getReturnCompanyInfo().getBravoId() + "/" + data.getReturnCompanyInfo().getCompanyName());
                                bw.newLine();
                                bw.flush();
                            }else{
                                writer.accept(supplierModel);
                            }
                        }

                    } else {
                        missingWriter.accept(supplierModel);
                    }
                } catch (Throwable e) {
                    System.out.println("Error while processing duns number " + duns + " " + e.getMessage());
                    try {
                        bw.write("cas error:");
                        if(null == duns)
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

        bw.close();
        writer.complete();
        missingWriter.complete();
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

    private void queryAndUpdateEmail(SupplierContactModel model) {
        if (supplierMap.containsKey(model.getEntityId()))
            return;
        supplierMap.put(model.getEntityId(), model.getEntityId());

        model.setEntityId(Util.getEntityId(model.getEntityId()));

        ReturnCompanyData companyData = queryCompanyFromJaggaer(model.getEntityId());
        if (null != companyData) {
            CompanyInfo info = companyData.getReturnCompanyInfo();
            if ((emailList.contains(info.getUserEmail().toLowerCase()) || emailList.contains(info.getBizEmail().toLowerCase()))) {
                System.out.println(model.getEntityId() + "," + info.getCompanyName() + "," + info.getUserEmail() + "," + info.getBizEmail());
                CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder builder = CreateUpdateCompanyRequest.builder();

                String emailAddress = model.getEntityId() + "_castest@yopmail.com";
                companyService.populateDetailsEmailUpdate(builder, info, emailAddress);
                CreateUpdateCompanyResponse response = companyService.createUpdateSupplier(builder.build());
                System.out.println(model.getEntityId() + ","
                        + model.getEmailAddress() + ","
                        + response.getBravoId() + ",\"" +
                        model.getSupplierName() + "\",\"" + model.getSupplierName() + "\"");
//            }
            }

        }
    }

    public void sync() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(new File(SUPPLIER_DATA_FILE)));

        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(MISSING_SUPPLIER_FILE)));

        BufferedWriter bc = new BufferedWriter(new FileWriter(new File(PROCESSED_SUPPLIER_FILE)));

        String dunsNumber = br.readLine();
        ArrayList<String> lst = new ArrayList<>();
        while (dunsNumber != null) {
            lst.add(dunsNumber);
            dunsNumber = br.readLine();
        }

        ForkJoinPool customThreadPool = new ForkJoinPool(3);

        customThreadPool.submit(
                () -> lst.parallelStream().forEach((duns) -> {
                    try {
                        ReturnCompanyData data = queryCompanyFromJaggaer(duns);
                        if (null != data) {
                            service.save(duns, data, null);
                            bc.write(duns);
                            bc.write("-");
                            bc.write(data.getReturnCompanyInfo().getBravoId());
                            bc.write(":");
                            bc.write(data.getReturnCompanyInfo().getCompanyName());
                            bc.newLine();
                            bc.flush();
                        } else {
                            try {
                                synchronized (bw) {
                                    bw.write("not found:");
                                    bw.write(duns);
                                    bw.newLine();
                                    bw.flush();
                                }
                            } catch (IOException e) {
                                System.out.println("company data not found for " + duns + " " + e.getMessage());
                            }
                        }
                    } catch (Throwable e) {
                        System.out.println("Error while processing duns number " + duns + " " + e.getMessage());
                        try {
                            bw.write("cas error:");
                            bw.write(duns);
                            bw.write(e.getMessage());
                            bw.newLine();
                        } catch (IOException ex) {
                        }

                    }
                })
        );


        customThreadPool.shutdown();
        customThreadPool.awaitTermination(2, TimeUnit.DAYS);
        bw.flush();
        bw.close();
        bc.flush();
        bc.close();
        System.out.println("Completed");
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

    public ReturnCompanyData queryCompanyFromJaggaer(String dunsNumber) {
        Optional<ReturnCompanyData> optCompany = companyService.getSupplierDataByDUNSNumber(dunsNumber);
        if (optCompany.isPresent()) {
            return optCompany.get();
        } else {
            return null;
        }
    }
}
