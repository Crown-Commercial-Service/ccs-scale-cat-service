package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierModel;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierReader;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.JaggaerSupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierSuggestionWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerCompanyService;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
public class JaggaerSupplierDownloader {
    private final JaggaerCompanyService companyService;

    public Map<String, JaggaerSupplierModel> getSuppliers(String baseFolder, String filename){
        JaggaerSupplierReader reader = new JaggaerSupplierReader();
        Map<String, JaggaerSupplierModel> result = new HashMap<>();
        File file = Paths.get(baseFolder, filename).toFile();
        reader.processFile(file, new Consumer<JaggaerSupplierModel>() {
            @Override
            public void accept(JaggaerSupplierModel jaggaerSupplierModel) {
                result.put(jaggaerSupplierModel.getBravoId(), jaggaerSupplierModel);
            }
        });
        return result;
    }

    public void downloadSuppliers(String baseFolder, String filename){
        int downloadedRecords = 0;
        JaggaerSupplierWriter writer = new JaggaerSupplierWriter(baseFolder, filename);
        GetCompanyDataResponse response = companyService.getAllSuppliers();
        int totalRecords = response.getTotRecords();
        downloadedRecords += response.getReturnedRecords();
        writeToFile(getModelList(response.getReturnCompanyData()), writer);

        while(downloadedRecords < totalRecords){
            System.out.println("starting "  + downloadedRecords);
            Set<ReturnCompanyData> companyDataList = getCompanyData(downloadedRecords);
            if(null == companyDataList)
                break;
            downloadedRecords += companyDataList.size();
            writeToFile(getModelList(companyDataList), writer);
        }
        writer.complete();
    }

    int retry = 0;

    @SneakyThrows
    private Set<ReturnCompanyData> getCompanyData(int downloadedRecords) {
        try {
            Set<ReturnCompanyData> companyDataList = companyService.getAllSuppliers(downloadedRecords);
            retry = 0;
            return companyDataList;
        }catch(RuntimeException re){
            re.printStackTrace();
            retry++;
            if(retry < 15){
                Thread.sleep(2000);
                return getCompanyData(downloadedRecords);
            }else{
                throw re;
            }
        }
    }

    private void writeToFile(List<JaggaerSupplierModel> modelList, JaggaerSupplierWriter writer) {
        for(JaggaerSupplierModel model : modelList)
            writer.accept(model);
    }


    private List<JaggaerSupplierModel> getModelList(Set<ReturnCompanyData> companyDataList){
        List<JaggaerSupplierModel> result = new ArrayList<>();
        companyDataList.forEach(companyData -> {
            JaggaerSupplierModel model = new JaggaerSupplierModel();
            CompanyInfo supplier = companyData.getReturnCompanyInfo();
            model.setSupplierName(supplier.getCompanyName());
            model.setBravoId(supplier.getBravoId());
            model.setExtCode(supplier.getExtCode());
            model.setExtUniqueCode(supplier.getExtUniqueCode());
            model.setPostalAddress(getValueOrEmpty(supplier.getAddress()) + ", " + getValueOrEmpty(supplier.getCity())
                    + ", " + getValueOrEmpty(supplier.getZip())
            );
            String email = null == supplier.getBizEmail() ? supplier.getUserEmail() : supplier.getBizEmail();
            if(null != email)
                email = email.trim();
            model.setEmail(email);
            result.add(model);
        });
        return result;
    }

    private String getValueOrEmpty(String value){
        if(null == value)
            return "";
        else
            return value.trim();
    }
}
