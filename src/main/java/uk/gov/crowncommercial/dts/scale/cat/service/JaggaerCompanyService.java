package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierContactModel;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.DUNS_PLACEHOLDER;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.EXTCODE_PLACEHOLDER;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.PRINCIPAL_PLACEHOLDER;
@Service
@RequiredArgsConstructor
public class JaggaerCompanyService {

    private final JaggaerAPIConfig jaggaerAPIConfig;

    private final JaggaerService jaggaerService;
    private final WebClient jaggaerWebClient;

    public CreateUpdateCompanyResponse createUpdateSupplier(CreateUpdateCompanyRequest company) {
        return jaggaerService.createUpdateCompany(company);
    }


    public void populateDetails(
            final CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder createUpdateCompanyRequestBuilder,
            String userName, String userEmail,String userPhone,
            String legalName,String dunsNumber,String webSite,
            String bizEmail,String bizPhone,String bizFax,
            String address,String city) {

        var companyInfoBuilder = CompanyInfo.builder();
        var createUpdateCompanyBuilder = CreateUpdateCompany.builder();

        createUpdateCompanyBuilder.operationCode(OperationCode.CREATE);
        companyInfoBuilder.userAlias(userName)
                .ssoCodeData(buildSSOCodeData(userName));

        CompanyType companyType = CompanyType.SELLER;
        companyInfoBuilder.companyName(legalName)
                .extCode(dunsNumber).type(companyType)
                .extUniqueCode(dunsNumber).type(companyType)
                .dAndBCode(dunsNumber)
                .bizEmail(bizEmail).bizPhone(bizPhone).bizFax(bizFax)
                .webSite(webSite)
                .address(address)
                .city(city)
                .userName(userName)
                .userEmail(userEmail)
                .userPhone(userPhone);

        createUpdateCompanyRequestBuilder
                .company(createUpdateCompanyBuilder.companyInfo(companyInfoBuilder.build()).build());
    }

    private static SSOCodeData buildSSOCodeData(final String userId) {
        return SSOCodeData.builder().ssoCode(Set.of(SSOCodeData.SSOCode.builder()
                .ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE).ssoUserLogin(userId).build())).build();
    }

    public Optional<ReturnCompanyData> getSupplierDataByDUNSNumber(final String concalveIdentifier) {
        // Get the supplier org from Jaggaer by the DUNS Number
        var getSupplierCompanyByDUNSNumberEndpoint = jaggaerAPIConfig
                .getGetCompanyProfileByDUNSNumber().get(JaggaerAPIConfig.ENDPOINT).replace(
                        DUNS_PLACEHOLDER, concalveIdentifier);
        return getSupplierDataHelper(getSupplierCompanyByDUNSNumberEndpoint);
    }

    public Optional<ReturnCompanyData> getSupplierDataByBravoId(final String bravoId) {
        var getSupplierCompanyByDUNSNumberEndpoint = jaggaerAPIConfig
                .getGetCompanyProfileByBravoID().get(JaggaerAPIConfig.ENDPOINT).replace(
                        PRINCIPAL_PLACEHOLDER, bravoId);
        return getSupplierDataHelper(getSupplierCompanyByDUNSNumberEndpoint);
    }

    public Optional<ReturnCompanyData> getSupplierDataByExtCode(final String concalveIdentifier) {
        // Get the supplier org from Jaggaer by the DUNS Number
        var getSupplierCompanyByDUNSNumberEndpoint = jaggaerAPIConfig
                .getGetCompanyProfileByExtCode().get(JaggaerAPIConfig.ENDPOINT).replace(
                        EXTCODE_PLACEHOLDER, concalveIdentifier);
        return getSupplierDataHelper(getSupplierCompanyByDUNSNumberEndpoint);
    }

    private Optional<ReturnCompanyData> getSupplierDataHelper(final String endpoint) {
        var response = ofNullable(
                jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
                        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error retrieving Jaggear supplier company data"));

        if ("0".equals(response.getReturnCode()) && "OK".equals(response.getReturnMessage())
                && response.getTotRecords() == 1 && response.getReturnCompanyData() != null) {

            return Optional.of(response.getReturnCompanyData().stream().findFirst().get());
        }
        return Optional.empty();
    }

    public GetCompanyDataResponse getAllSuppliers() {
        final String endpoint = "/esop/jint/api/public/ja/v1/companyprofiles?comp=USER;SSO_CODE";
        GetCompanyDataResponse response = ofNullable(
                jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
                        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error retrieving Jaggear supplier company data"));



        if ("0".equals(response.getReturnCode()) && "OK".equals(response.getReturnMessage())
                && response.getReturnCompanyData() != null) {
            return response;
        }
        return null;
    }

    public Set<ReturnCompanyData> getAllSuppliers(int start) {
        final String endpoint = "/esop/jint/api/public/ja/v1/companyprofiles?comp=USER;SSO_CODE&start=" + start;
        var response = ofNullable(
                jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
                        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error retrieving Jaggear supplier company data"));

        if ("0".equals(response.getReturnCode()) && "OK".equals(response.getReturnMessage())
                 && response.getReturnCompanyData() != null) {

            return response.getReturnCompanyData();
        }
        return null;
    }

    public void populateDetailsEmailUpdate(CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder builder, CompanyInfo info, String emailAddress) {
        var companyInfoBuilder = CompanyInfo.builder();
        var createUpdateCompanyBuilder = CreateUpdateCompany.builder();

        String bizEmail = emailAddress;
        String userEmail = emailAddress;
        companyInfoBuilder.bravoId(info.getBravoId());
        createUpdateCompanyBuilder.operationCode(OperationCode.UPDATE);

        CompanyType companyType = CompanyType.SELLER;
        companyInfoBuilder
                .bizEmail(bizEmail)
                .userEmail(userEmail);
        builder
                .company(createUpdateCompanyBuilder.companyInfo(companyInfoBuilder.build()).build());
    }

    public void populateDetails(CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder builder, SupplierContactModel model) {
        var companyInfoBuilder = CompanyInfo.builder();
        var createUpdateCompanyBuilder = CreateUpdateCompany.builder();
        String userName = model.getEmailAddress();
        String legalName = model.getSupplierName();
        String dunsNumber = model.getEntityId();
        String bizEmail = model.getEmailAddress();
        String bizPhone = model.getPhoneNumber();
        String bizFax = null;
        String webSite = model.getWebsite();
        String address = null;
        String city = null;
        String userEmail = model.getEmailAddress();
        String userPhone = model.getPhoneNumber();

        createUpdateCompanyBuilder.operationCode(OperationCode.CREATE);
        companyInfoBuilder.userAlias(userName)
                .ssoCodeData(buildSSOCodeData(userName));

        CompanyType companyType = CompanyType.SELLER;
        companyInfoBuilder.companyName(legalName)
                .extCode(dunsNumber).type(companyType)
                .extUniqueCode(dunsNumber).type(companyType)
                .dAndBCode(dunsNumber)
                .bizEmail(bizEmail).bizPhone(bizPhone).bizFax(bizFax)
                .webSite(webSite)
                .address(address)
                .city(city)
                .userName(userName)
                .userEmail(userEmail)
                .userPhone(userPhone);

        builder
                .company(createUpdateCompanyBuilder.companyInfo(companyInfoBuilder.build()).build());
    }
}
