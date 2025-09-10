package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierDunsUpdate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Web (mock MVC) Suppliers controller tests
 */
@WebMvcTest(SuppliersController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, GlobalErrorHandler.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
public class SuppliersControllerTests {
    @Autowired
    private MockMvc mockMvc;

    private static final String PRINCIPAL = "test@testmail.com";
    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validCATJwtReqPostProcessor;

    @BeforeEach
    public void beforeEach() {
        validCATJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER")).jwt(jwt -> jwt.subject(PRINCIPAL));
    }

    @Test
    public void whenUpdateSupplierDunsMappings_WithInvalidData_ErrorThrown() throws Exception {
        SupplierDunsUpdate requestData = new SupplierDunsUpdate();
        requestData.setCurrentDunsNumber("12345");

        ObjectMapper mapper = new ObjectMapper();
        String requestJson = mapper.writeValueAsString(requestData);

        mockMvc
                .perform(put("/suppliers/update-duns", PRINCIPAL).with(validCATJwtReqPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .accept(APPLICATION_JSON))
                .andDo(print()).andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.errors[0].detail", containsString(Constants.ERR_MSG_INCOMPLETE_DATA)));
    }

    @Test
    public void whenUpdateSupplierDunsMappings_WithValidData_SuccessReturned() throws Exception {
        SupplierDunsUpdate requestData = new SupplierDunsUpdate();
        requestData.setCurrentDunsNumber("12345");
        requestData.setReplacementDunsNumber("678910");

        ObjectMapper mapper = new ObjectMapper();
        String requestJson = mapper.writeValueAsString(requestData);

        mockMvc
                .perform(put("/suppliers/update-duns", PRINCIPAL).with(validCATJwtReqPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .accept(APPLICATION_JSON))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().string(Constants.OK_MSG));
    }
}