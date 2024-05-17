package uk.gov.crowncommercial.dts.scale.cat.utils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.BusinessUnit;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.CreateJIBuyerRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.Department;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.International;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.JiUserList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.MobileTelephoneNumber;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.TelephoneNumber;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.UserStatus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@UtilityClass
@Slf4j
public class JIUserRequestBuilder {

    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private static final String AREA_CODE = "0";
    public static final String COUNTRY_CODE = "+44";
    public static final String COUNTRY_CODE_PREFIX = "+";

    public CreateJIBuyerRequest mapToJIBuyerRequest(JaggaerAPIConfig jaggaerAPIConfig, CreateUpdateCompanyRequest requestBuilder) {
        List<SubUsers.SubUser> subUsers = requestBuilder.getSubUsers().getSubUsers().stream().toList();
        Set<JiUserList.JiUser> users = subUsers.stream().map(subUser -> JiUserList.JiUser.builder()
                .userName(subUser.getLogin())
                .firstName(subUser.getName())
                .lastName(subUser.getSurName())
                .email(subUser.getEmail())
                .userStatus(UserStatus.ACTIVE.getValue())
                .international(International.builder().country("GB").language("en_GB").build())
                .authorizationMethod("JAAuthLogin")
                .businessUnit(BusinessUnit.builder().internalName(jaggaerAPIConfig.getBusinessUnitName()).build())
                .department(Department.builder().departmentName("Division Self Serve").build())
                .position(jaggaerAPIConfig.getDefaultBuyerRightsProfile())
                .telephoneNumber(nonNull(subUser.getPhoneNumber()) ? buildTelephoneNumber(subUser.getPhoneNumber()) : null)
                .mobileTelephoneNumber(nonNull(subUser.getMobilePhoneNumber()) ? buildMobileTelephoneNumber(subUser.getMobilePhoneNumber()) : null)
                .build()).collect(Collectors.toSet());
        JiUserList userList = JiUserList.builder().jiUser(users).build();
        return CreateJIBuyerRequest.builder().jiuserList(userList).build();
    }

    TelephoneNumber buildTelephoneNumber(String telephoneNumber) {
        TelephoneNumber.TelephoneNumberBuilder<?, ?> builder = TelephoneNumber.builder();
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(telephoneNumber, "ZZ");
            String significantNumber = phoneNumberUtil.getNationalSignificantNumber(phoneNumber);
            int areaCodeLength = phoneNumberUtil.getLengthOfGeographicalAreaCode(phoneNumber);
            if (areaCodeLength > 0) {
                builder.areaCode(AREA_CODE + significantNumber.substring(0, areaCodeLength));
                builder.number(significantNumber.substring(areaCodeLength));
            } else {
                builder.areaCode(AREA_CODE);
                builder.number(significantNumber);
            }
            return builder.countryCode(COUNTRY_CODE_PREFIX + phoneNumber.getCountryCode()).build();
        } catch (NumberParseException e) {
            log.error("Error while converting phone number: {}", telephoneNumber);
            return builder.countryCode(COUNTRY_CODE).number(telephoneNumber).areaCode(AREA_CODE).build();
        }
    }

    MobileTelephoneNumber buildMobileTelephoneNumber(String telephoneNumber) {
        MobileTelephoneNumber.MobileTelephoneNumberBuilder<?, ?> builder = MobileTelephoneNumber.builder();
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(telephoneNumber, "ZZ");
            String significantNumber = phoneNumberUtil.getNationalSignificantNumber(phoneNumber);
            int areaCodeLength = phoneNumberUtil.getLengthOfGeographicalAreaCode(phoneNumber);
            if (areaCodeLength > 0) {
                builder.areaCode(AREA_CODE + significantNumber.substring(0, areaCodeLength));
                builder.number(significantNumber.substring(areaCodeLength));
            } else {
                builder.areaCode(AREA_CODE);
                builder.number(significantNumber);
            }
            return builder.countryCode(COUNTRY_CODE_PREFIX + phoneNumber.getCountryCode()).build();
        } catch (NumberParseException e) {
            log.error("Error while converting mobile phone number: {}", telephoneNumber);
            return builder.countryCode(COUNTRY_CODE).number(telephoneNumber).areaCode(AREA_CODE).build();
        }
    }

}