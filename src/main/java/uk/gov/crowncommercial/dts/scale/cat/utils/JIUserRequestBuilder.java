package uk.gov.crowncommercial.dts.scale.cat.utils;

import lombok.experimental.UtilityClass;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.BusinessUnit;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.CreateJIBuyerRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.Department;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.International;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.JiUserList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus.UserStatus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class JIUserRequestBuilder {

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
                .businessUnit(BusinessUnit.builder().internalName(jaggaerAPIConfig.getDefaultBusinessUnitInternalName()).build())
                .department(Department.builder().departmentName("Division Self Serve").build())
                .position(jaggaerAPIConfig.getDefaultBuyerRightsProfile())
                .build()).collect(Collectors.toSet());
        JiUserList userList = JiUserList.builder().jiUser(users).build();
        return CreateJIBuyerRequest.builder().jiuserList(userList).build();
    }
}
