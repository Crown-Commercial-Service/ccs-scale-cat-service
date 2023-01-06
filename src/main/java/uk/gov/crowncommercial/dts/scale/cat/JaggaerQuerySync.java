package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierContactModel;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerCompanyService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class JaggaerQuerySync implements Consumer<SupplierContactModel> {
    private final JaggaerCompanyService companyService;

    Semaphore readJaggaerLock = new Semaphore(2);
    private final Logger errorLogger = LoggerFactory.getLogger("ERROR_LOGGER");
    private final SaveService service;
    private final Map<String, String> supplierMap = new HashMap<>();

    @Override
    public void accept(SupplierContactModel supplierContactModel) {
        try {
            this.queryAndCreate(supplierContactModel);
        } catch (JaggaerApplicationException jae) {
            log.error("Error while processing record {}:{}", supplierContactModel.getEntityId(), supplierContactModel.getSupplierName());
            log.error("Error details: {}", jae.getMessage());
        } catch (Throwable t) {
            errorLogger.error("Error while processing record {}:{}", supplierContactModel.getEntityId(), supplierContactModel.getSupplierName());
            throw t;
        }
    }


    private void queryAndCreate(SupplierContactModel model) {
        synchronized (supplierMap) {
            if (supplierMap.containsKey(model.getEntityId()))
                return;
            supplierMap.put(model.getEntityId(), model.getEntityId());
        }
        model.setEntityId(Util.getEntityId(model.getEntityId()));

        OrganisationMapping om = service.query(model.getEntityId());
        if (null != om)
            return;

        ReturnCompanyData companyData;
        try{
            readJaggaerLock.acquire();
            companyData = queryCompanyFromJaggaer(model.getEntityId());
        }catch (InterruptedException ie){
            companyData = queryCompanyFromJaggaer(model.getEntityId());
        }finally{
            readJaggaerLock.release();
        }


        if (null != companyData) {
            String bravoId = companyData.getReturnCompanyInfo().getBravoId();
            service.save(model.getEntityId(), companyData);

            System.out.println(model.getEntityId() + ","
                    + companyData.getReturnCompanyInfo().getBizEmail() + ","
                    + bravoId + ",\"" +
                    model.getSupplierName() + "\",\"" + companyData.getReturnCompanyInfo().getCompanyName() + "\"");
        } else {
            log.error("Company details not available for {}", model.getEntityId());
        }
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
