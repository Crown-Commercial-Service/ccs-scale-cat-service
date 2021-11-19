package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnSubUser.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.util.TestUtils;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ProcurementProjectService.class, JaggaerAPIConfig.class,
    TendersAPIModelUtils.class, RetryableTendersDBDelegate.class, ModelMapper.class,
    JaggaerService.class, ApplicationFlagsConfig.class}, webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ProcurementProjectServiceTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String TENDER_CODE = "tender_0001";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String ORG = "CCS";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER + '-' + ORG;
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String UPDATED_PROJECT_NAME = "New name";
  private static final String BUYER_COMPANY_BRAVO_ID = "54321";
  private static final CreateEvent CREATE_EVENT = new CreateEvent();
  private static final Optional<SubUser> JAGGAER_USER =
      Optional.of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).build());
  private static final ReturnCompanyInfo BUYER_COMPANY_INFO =
      ReturnCompanyInfo.builder().bravoId(BUYER_COMPANY_BRAVO_ID).build();

  private final AgreementDetails agreementDetails = new AgreementDetails();

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private AgreementsServiceAPIConfig agreementsServiceAPIConfig;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @Autowired
  private ProcurementProjectService procurementProjectService;

  @BeforeEach
  void beforeEach() {
    agreementDetails.setAgreementId(CA_NUMBER);
    agreementDetails.setLotId(LOT_NUMBER);
  }

  @Test
  void testCreateFromAgreementDetails() throws Exception {

    // Stub some objects
    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");
    createUpdateProjectResponse.setTenderCode(TENDER_CODE);
    createUpdateProjectResponse.setTenderReferenceCode(TENDER_REF_CODE);

    var procurementProject =
        ProcurementProject.of(agreementDetails, TENDER_CODE, TENDER_REF_CODE, PROJ_NAME, PRINCIPAL);
    procurementProject.setId(PROC_PROJECT_ID);

    var eventSummary = new EventSummary();
    eventSummary.setId(EVENT_OCID);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);
    when(procurementProjectRepo.save(any(ProcurementProject.class))).thenReturn(procurementProject);
    when(procurementEventService.createEvent(PROC_PROJECT_ID, CREATE_EVENT, null, PRINCIPAL))
        .thenReturn(eventSummary);

    // Invoke
    var draftProcurementProject =
        procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL);

    // Assert
    assertEquals(PROC_PROJECT_ID, draftProcurementProject.getProcurementID());
    assertEquals(EVENT_OCID, draftProcurementProject.getEventId());
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + '-' + ORG,
        draftProcurementProject.getDefaultName().getName());
    assertEquals(CA_NUMBER,
        draftProcurementProject.getDefaultName().getComponents().getAgreementId());
    assertEquals(LOT_NUMBER, draftProcurementProject.getDefaultName().getComponents().getLotId());
    assertEquals(ORG, draftProcurementProject.getDefaultName().getComponents().getOrg());

    // Verify
    verify(userProfileService).resolveBuyerUserByEmail(PRINCIPAL);
    verify(procurementProjectRepo).save(any(ProcurementProject.class));
    verify(procurementEventService).createEvent(PROC_PROJECT_ID, CREATE_EVENT, null, PRINCIPAL);
  }

  @Test
  void testCreateFromAgreementDetailsThrowsJaggaerApplicationException() throws Exception {
    // Stub some objects
    var jaggaerErrorResponse = new CreateUpdateProjectResponse();
    jaggaerErrorResponse.setReturnCode(1);
    jaggaerErrorResponse.setReturnMessage("NOT OK");

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(jaggaerErrorResponse);

    // Invoke & assert
    var jagEx = assertThrows(JaggaerApplicationException.class,
        () -> procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL));
    assertEquals("Jaggaer application exception, Code: [1], Message: [NOT OK]", jagEx.getMessage());
  }

  @Test
  void testUpdateProcurementProjectName() throws Exception {

    // Stub some objects
    var procurementProject = new ProcurementProject();

    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");

    var tender = Tender.builder().title(UPDATED_PROJECT_NAME).build();
    var createUpdateProject =
        new CreateUpdateProject(OperationCode.UPDATE, Project.builder().tender(tender).build());

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(argThat(new UpdateProjectMatcher(createUpdateProject))).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);

    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      return Optional.of(procurementProject);
    });

    // Invoke
    ArgumentCaptor<ProcurementProject> captor = ArgumentCaptor.forClass(ProcurementProject.class);

    procurementProjectService.updateProcurementProjectName(PROC_PROJECT_ID, UPDATED_PROJECT_NAME,
        PRINCIPAL);

    // Verify that entity was updated as expected
    verify(procurementProjectRepo).save(captor.capture());
    assertEquals(UPDATED_PROJECT_NAME, captor.getValue().getProjectName());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

  }

  @Test
  void testUpdateProcurementProjectNameThrowsIllegalArgumentException() throws Exception {

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(IllegalArgumentException.class, () -> procurementProjectService
        .updateProcurementProjectName(PROC_PROJECT_ID, null, PRINCIPAL));
    assertEquals("New project name must be supplied", ex.getMessage());
  }

  @Test
  void testUpdateProcurementEventNameThrowsResourceNotFoundApplicationException() throws Exception {

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(ResourceNotFoundException.class, () -> procurementProjectService
        .updateProcurementProjectName(PROC_PROJECT_ID, UPDATED_PROJECT_NAME, PRINCIPAL));
    assertEquals("Project '1' not found", ex.getMessage());
  }

  @Test
  void testGetProjectEventTypes() throws JsonProcessingException {

    // Mock behaviours
    var procurementProject = new ProcurementProject();
    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      procurementProject.setCaNumber(CA_NUMBER);
      procurementProject.setLotNumber(LOT_NUMBER);
      return Optional.of(procurementProject);
    });

    when(jaggaerWebClient.get()
        .uri(agreementsServiceAPIConfig.getGetEventTypesForAgreement().get("uriTemplate"),
            CA_NUMBER, LOT_NUMBER)
        .retrieve().bodyToMono(eq(ProjectEventType[].class))
        .block(Duration.ofSeconds(agreementsServiceAPIConfig.getTimeoutDuration())))
            .thenReturn(TestUtils.getProjectEvents());

    // Invoke
    var projectEventTypes = procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID);

    // Verify
    verify(procurementProjectRepo).findById(PROC_PROJECT_ID);

    // TODO : better way to compare ??
    var defineEventTypes = projectEventTypes.stream()
        .map(eventType -> eventType.getType().getValue()).collect(Collectors.joining(","));
    var eventTypesDescription =
        projectEventTypes.stream().map(EventType::getDescription).collect(Collectors.joining(","));
    var expectedEventTypes = TestUtils.getEventTypes().stream()
        .map(eventType -> eventType.getType().getValue()).collect(Collectors.joining(","));
    var expectedEventTypesDescription = TestUtils.getEventTypes().stream()
        .map(EventType::getDescription).collect(Collectors.joining(","));
    assertEquals(defineEventTypes, expectedEventTypes);
    assertEquals(eventTypesDescription, expectedEventTypesDescription);

  }

  @Test
  void testGetProjectEventTypesThrowsResourceNotFoundApplicationException() throws Exception {

    // Mock behaviours
    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> Optional.ofNullable(null));

    // Invoke & assert
    var ex = assertThrows(ResourceNotFoundException.class,
        () -> procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID));
    assertEquals("Project '1' not found", ex.getMessage());

  }

  /**
   * Custom matcher to verify the object sent to Jaggaer to update a project.
   *
   */
  private class UpdateProjectMatcher implements ArgumentMatcher<CreateUpdateProject> {

    private final CreateUpdateProject left;

    UpdateProjectMatcher(final CreateUpdateProject left) {
      this.left = left;
    }

    @Override
    public boolean matches(final CreateUpdateProject right) {
      return left.getProject().getTender().getTitle()
          .equals(right.getProject().getTender().getTitle())
          && right.getOperationCode() == OperationCode.UPDATE;
    }
  }
}
