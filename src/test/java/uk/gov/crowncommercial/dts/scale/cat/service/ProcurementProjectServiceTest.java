package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.data.domain.Pageable;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationIdentifier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DashboardStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.util.TestUtils;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(
    classes = {ProcurementProjectService.class, JaggaerAPIConfig.class, TendersAPIModelUtils.class,
        ModelMapper.class, JaggaerService.class, ApplicationFlagsConfig.class,EventTransitionService.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ProcurementProjectServiceTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String CONCLAVE_ORG_ID = "654891633619851306"; // Internal PPG ID
  private static final String CONCLAVE_ORG_SCHEME = "US-DUNS";
  private static final String CONCLAVE_ORG_SCHEME_ID = "123456789";
  private static final String CONCLAVE_ORG_LEGAL_ID =
      CONCLAVE_ORG_SCHEME + '-' + CONCLAVE_ORG_SCHEME_ID;
  private static final String CONCLAVE_ORG_NAME = "ACME Products Ltd";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String JAGGAER_BUYER_COMPANY_ID = "2345678";
  private static final String TENDER_CODE = "tender_0001";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER + '-' + CONCLAVE_ORG_NAME;
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final String EVENT_NAME = "Test Event";
  private static final Integer EVENT_ID = 1;
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String UPDATED_PROJECT_NAME = "New name";
  private static final CreateEvent CREATE_EVENT = new CreateEvent();
  private static final OrganisationMapping ORG_MAPPING = OrganisationMapping.builder()
      .externalOrganisationId(Integer.valueOf(JAGGAER_BUYER_COMPANY_ID))
      .organisationId(CONCLAVE_ORG_ID).build();
  private static final AgreementDetails AGREEMENT_DETAILS = new AgreementDetails();
  private static final Optional<SubUser> JAGGAER_USER =
      Optional.of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).build());
  private static final CompanyInfo BUYER_COMPANY_INFO =
      CompanyInfo.builder().bravoId(JAGGAER_BUYER_COMPANY_ID).build();

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private AgreementsServiceAPIConfig agreementsServiceAPIConfig;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private EventTransitionService eventTransitionService;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @Autowired
  private ProcurementProjectService procurementProjectService;



  @MockBean
  private JaggaerService jaggaerService;

  @BeforeAll
  static void beforeAll() {
    AGREEMENT_DETAILS.setAgreementId(CA_NUMBER);
    AGREEMENT_DETAILS.setLotId(LOT_NUMBER);
  }

  @Test
  void testCreateFromAgreementDetails() throws Exception {

    // Stub some objects
    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");
    createUpdateProjectResponse.setTenderCode(TENDER_CODE);
    createUpdateProjectResponse.setTenderReferenceCode(TENDER_REF_CODE);

    var procurementProject = ProcurementProject.builder()
        .caNumber(AGREEMENT_DETAILS.getAgreementId()).lotNumber(AGREEMENT_DETAILS.getLotId())
        .externalProjectId(TENDER_CODE).externalReferenceId(TENDER_REF_CODE).projectName(PROJ_NAME)
        .createdBy(PRINCIPAL).createdAt(Instant.now()).updatedBy(PRINCIPAL).updatedAt(Instant.now())
        .procurementEvents(Set.of(ProcurementEvent.builder().eventType("FC").id(1).build()))
        .build();
    procurementProject.setId(PROC_PROJECT_ID);

    var eventSummary = new EventSummary();
    eventSummary.setId(EVENT_OCID);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerUserCompany(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);

    when(conclaveService.getOrganisationIdentity(CONCLAVE_ORG_ID))
        .thenReturn(Optional.of(new OrganisationProfileResponseInfo()
            .identifier(new OrganisationIdentifier().legalName(CONCLAVE_ORG_NAME)
                .scheme(CONCLAVE_ORG_SCHEME).id(CONCLAVE_ORG_SCHEME_ID))
            .detail(new OrganisationDetail().organisationId(CONCLAVE_ORG_ID))));
    when(conclaveService.getOrganisationIdentifer(any(OrganisationProfileResponseInfo.class)))
        .thenReturn(CONCLAVE_ORG_LEGAL_ID);
    when(retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(CONCLAVE_ORG_LEGAL_ID))
        .thenReturn(Optional.of(ORG_MAPPING));
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);
    when(retryableTendersDBDelegate.save(any(ProcurementProject.class)))
        .thenReturn(procurementProject);
    when(procurementEventService.createEvent(PROC_PROJECT_ID, CREATE_EVENT, null, PRINCIPAL))
        .thenReturn(eventSummary);

    // Invoke
    var draftProcurementProject = procurementProjectService
        .createFromAgreementDetails(AGREEMENT_DETAILS, PRINCIPAL, CONCLAVE_ORG_ID);

    // Assert
    assertEquals(PROC_PROJECT_ID, draftProcurementProject.getProcurementID());
    assertEquals(EVENT_OCID, draftProcurementProject.getEventId());
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + '-' + CONCLAVE_ORG_NAME,
        draftProcurementProject.getDefaultName().getName());
    assertEquals(CA_NUMBER,
        draftProcurementProject.getDefaultName().getComponents().getAgreementId());
    assertEquals(LOT_NUMBER, draftProcurementProject.getDefaultName().getComponents().getLotId());
    assertEquals(CONCLAVE_ORG_NAME,
        draftProcurementProject.getDefaultName().getComponents().getOrg());

    // Verify
    verify(userProfileService).resolveBuyerUserProfile(PRINCIPAL);
    verify(userProfileService).resolveBuyerUserCompany(PRINCIPAL);
    verify(conclaveService).getOrganisationIdentity(CONCLAVE_ORG_ID);

    var captor = ArgumentCaptor.forClass(ProcurementProject.class);
    verify(retryableTendersDBDelegate, times(1)).save(captor.capture());
    var capturedProcProject = captor.getValue();
    assertEquals(CA_NUMBER, capturedProcProject.getCaNumber());
    assertEquals(LOT_NUMBER, capturedProcProject.getLotNumber());
    assertEquals(ORG_MAPPING, capturedProcProject.getOrganisationMapping());
    assertEquals(PRINCIPAL, capturedProcProject.getCreatedBy());
    assertEquals(PRINCIPAL, capturedProcProject.getUpdatedBy());

    verify(procurementEventService).createEvent(PROC_PROJECT_ID, CREATE_EVENT, null, PRINCIPAL);
  }

  @Test
  void testCreateFromAgreementDetailsThrowsJaggaerApplicationException() throws Exception {
    // Stub some objects
    var jaggaerErrorResponse = new CreateUpdateProjectResponse();
    jaggaerErrorResponse.setReturnCode(1);
    jaggaerErrorResponse.setReturnMessage("NOT OK");

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerUserCompany(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(conclaveService.getOrganisationIdentity(CONCLAVE_ORG_ID))
        .thenReturn(Optional.of(new OrganisationProfileResponseInfo()
            .identifier(new OrganisationIdentifier().legalName(CONCLAVE_ORG_NAME)
                .scheme(CONCLAVE_ORG_SCHEME).id(CONCLAVE_ORG_SCHEME_ID))
            .detail(new OrganisationDetail().organisationId(CONCLAVE_ORG_ID))));
    when(conclaveService.getOrganisationIdentifer(any(OrganisationProfileResponseInfo.class)))
        .thenReturn(CONCLAVE_ORG_LEGAL_ID);
    when(retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(CONCLAVE_ORG_ID))
        .thenReturn(Optional.of(ORG_MAPPING));
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(jaggaerErrorResponse);

    // Invoke & assert
    var jagEx = assertThrows(JaggaerApplicationException.class, () -> procurementProjectService
        .createFromAgreementDetails(AGREEMENT_DETAILS, PRINCIPAL, CONCLAVE_ORG_ID));
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
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(argThat(new UpdateProjectMatcher(createUpdateProject))).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);

    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      return Optional.of(procurementProject);
    });

    // Invoke
    ArgumentCaptor<ProcurementProject> captor = ArgumentCaptor.forClass(ProcurementProject.class);

    procurementProjectService.updateProcurementProjectName(PROC_PROJECT_ID, UPDATED_PROJECT_NAME,
        PRINCIPAL);

    // Verify that entity was updated as expected
    verify(retryableTendersDBDelegate).save(captor.capture());
    assertEquals(UPDATED_PROJECT_NAME, captor.getValue().getProjectName());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

  }

  @Test
  void testUpdateProcurementProjectNameThrowsIllegalArgumentException() throws Exception {

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(IllegalArgumentException.class, () -> procurementProjectService
        .updateProcurementProjectName(PROC_PROJECT_ID, null, PRINCIPAL));
    assertEquals("New project name must be supplied", ex.getMessage());
  }

  @Test
  void testUpdateProcurementEventNameThrowsResourceNotFoundApplicationException() throws Exception {

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(ResourceNotFoundException.class, () -> procurementProjectService
        .updateProcurementProjectName(PROC_PROJECT_ID, UPDATED_PROJECT_NAME, PRINCIPAL));
    assertEquals("Project '1' not found", ex.getMessage());
  }

  @Test
  void testGetProjectEventTypes() throws JsonProcessingException {

    // Mock behaviours
    var procurementProject = new ProcurementProject();
    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      procurementProject.setCaNumber(CA_NUMBER);
      procurementProject.setLotNumber(LOT_NUMBER);
      return Optional.of(procurementProject);
    });

    when(agreementsService.getLotEventTypes(CA_NUMBER, LOT_NUMBER))
        .thenReturn(TestUtils.getLotEventTypes());

    // Invoke
    var projectEventTypes = procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID);

    // Verify
    verify(retryableTendersDBDelegate).findProcurementProjectById(PROC_PROJECT_ID);

    assertEquals(TestUtils.getEventTypes(), new HashSet<>(projectEventTypes));
  }

  @Test
  void testGetProjectEventTypesThrowsResourceNotFoundApplicationException() throws Exception {

    // Mock behaviours
    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID))
        .then(mock -> Optional.ofNullable(null));

    // Invoke & assert
    var ex = assertThrows(ResourceNotFoundException.class,
        () -> procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID));
    assertEquals("Project '1' not found", ex.getMessage());

  }

  @Test
  void testGetProjectsFromJaggaer() {

    // Mock behaviours
    var event = new ProcurementEvent();
    event.setId(EVENT_ID);
    event.setExternalEventId("rfq_0001");
    event.setEventName(EVENT_NAME);
    event.setEventType("RFI");
    event.setOcdsAuthorityName("ocds");
    event.setOcidPrefix("pfhb7i");
    event.setTenderStatus("planned");
    event.setUpdatedAt(Instant.now());

    Set<ProcurementEvent> events = new HashSet<>();
    events.add(event);

    var exportRfxResponse = new ExportRfxResponse();
    exportRfxResponse.setRfxSetting(RfxSetting.builder().statusCode(0).build());

    var project = ProcurementProject.builder().id(PROC_PROJECT_ID).projectName(PROJ_NAME)
        .externalProjectId("Test").procurementEvents(events).build();
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(retryableTendersDBDelegate
        .findProjectUserMappingByUserId(eq(JAGGAER_USER.get().getUserId()),any(),any(), any(Pageable.class)))
            .thenReturn(List.of(ProjectUserMapping.builder()
                .project(ProcurementProject.builder().id(1).procurementEvents(events).build()).id(1)
                .userId("1234").build()));
    //when(jaggaerService.getRfx(event.getExternalEventId())).thenReturn(exportRfxResponse);
    when(retryableTendersDBDelegate.findByExternalProjectIdIn(any(Set.class)))
        .thenReturn(List.of(project));

    var response = procurementProjectService.getProjects(PRINCIPAL, null, null, "0", "20");

    assertNotNull(response);
    assertEquals(1, response.size());

  }

  @Test
  void testDeleteUserMapping() {
    var procurementProject = ProcurementProject.builder().externalProjectId(TENDER_CODE)
        .externalReferenceId(TENDER_REF_CODE).projectName(PROJ_NAME).createdBy(PRINCIPAL)
        .procurementEvents(Set.of(
            ProcurementEvent.builder().eventType("FC").id(1).externalEventId("itt_123").build()))
        .build();
    procurementProject.setId(PROC_PROJECT_ID);
    var tender = Tender.builder().projectOwner(ProjectOwner.builder().id("2").build()).build();
    var pro = Project.builder().tender(tender).build();

    var userMapping = ProjectUserMapping.builder().id(1).userId("6").project(procurementProject)
        .timestamps(Timestamps.createTimestamps(PRINCIPAL)).build();

    Optional<SubUser> JAGGAER_USER_1 =
        Optional.of(SubUser.builder().userId("6").email(PRINCIPAL).build());
    
    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER_1);
    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID))
        .thenReturn(Optional.of(procurementProject));
    when(retryableTendersDBDelegate.save(any(ProjectUserMapping.class))).thenReturn(userMapping);
    when(jaggaerService.getProject(procurementProject.getExternalProjectId())).thenReturn(pro);
    when(retryableTendersDBDelegate.findProjectUserMappingByProjectIdAndUserId(PROC_PROJECT_ID,
        "6")).thenReturn(Optional.of(userMapping));
    
    var exportRfxResponse = new ExportRfxResponse();
    exportRfxResponse.setRfxSetting(RfxSetting.builder().statusCode(0).build());
    exportRfxResponse.setEmailRecipientList(EmailRecipientList.builder()
        .emailRecipient(
            Arrays.asList(EmailRecipient.builder().user(User.builder().id("6").build()).build()))
        .build());

    when(jaggaerService.getRfxWithEmailRecipients("itt_123")).thenReturn(exportRfxResponse);

    procurementProjectService.deleteTeamMember(PROC_PROJECT_ID, PRINCIPAL, PRINCIPAL);
    
    var rfxRequest = Rfx.builder()
        .rfxSetting(RfxSetting.builder().rfxId("itt_123").rfxReferenceCode(null).build())
        .emailRecipientList(EmailRecipientList.builder().emailRecipient(new ArrayList<>()).build())
        .build();

    // Verify
    verify(retryableTendersDBDelegate).findProjectUserMappingByProjectIdAndUserId(
        procurementProject.getId(), JAGGAER_USER_1.get().getUserId());
    verify(retryableTendersDBDelegate).save(any(ProjectUserMapping.class));
    verify(jaggaerService).createUpdateRfx(rfxRequest, OperationCode.UPDATE_RESET);
  }

  @Test
  void testGetTeamMembers() {
    var procurementProject = ProcurementProject.builder().externalProjectId(TENDER_CODE)
        .externalReferenceId(TENDER_REF_CODE).projectName(PROJ_NAME).createdBy(PRINCIPAL)
        .procurementEvents(Set.of(
            ProcurementEvent.builder().eventType("FC").id(1).externalEventId("itt_123").build()))
        .build();
    procurementProject.setId(PROC_PROJECT_ID);
    var tender = Tender.builder().projectOwner(ProjectOwner.builder().id("2").build()).build();
    var team =
        ProjectTeam.builder().user(new HashSet<>(Arrays.asList(User.builder().id("1").build(),
            User.builder().id("2").build(), User.builder().id("3").build()))).build();
    var pro = Project.builder().tender(tender).projectTeam(team).build();
    var userMapping = ProjectUserMapping.builder().id(1).userId("12345").project(procurementProject)
        .timestamps(Timestamps.createTimestamps(PRINCIPAL)).build();
    var userMapping1 =
        ProjectUserMapping.builder().id(2).userId("12346").project(procurementProject).deleted(true)
            .timestamps(Timestamps.createTimestamps(PRINCIPAL)).build();
    var userMapping2 = ProjectUserMapping.builder().id(3).userId("12347")
        .project(procurementProject).timestamps(Timestamps.createTimestamps(PRINCIPAL)).build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID))
        .thenReturn(Optional.of(procurementProject));
    when(retryableTendersDBDelegate.save(any(ProjectUserMapping.class))).thenReturn(userMapping);
    when(jaggaerWebClient.get()
        .uri(jaggaerAPIConfig.getGetProject().get("endpoint"),
            procurementProject.getExternalProjectId())
        .retrieve().bodyToMono(eq(Project.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))).thenReturn(pro);
    when(retryableTendersDBDelegate.findProjectUserMappingByProjectIdAndUserId(PROC_PROJECT_ID,
        "12345")).thenReturn(Optional.of(userMapping));
    when(userProfileService.resolveBuyerUserByUserId(any()))
        .thenReturn(Optional.of(SubUsers.SubUser.builder().phoneNumber("123456").build()));
    when(conclaveService.getUserProfile(any()))
        .thenReturn(Optional.of(new UserProfileResponseInfo()));

    var exportRfxResponse = new ExportRfxResponse();
    exportRfxResponse.setRfxSetting(RfxSetting.builder().statusCode(0).build());
    exportRfxResponse.setEmailRecipientList(EmailRecipientList.builder()
        .emailRecipient(
            Arrays.asList(EmailRecipient.builder().user(User.builder().id("6").build()).build()))
        .build());

    when(jaggaerService.getRfxWithEmailRecipients("itt_123")).thenReturn(exportRfxResponse);
    when(retryableTendersDBDelegate.findProjectUserMappingByProjectId(PROC_PROJECT_ID))
        .thenReturn(new HashSet<>(Arrays.asList(userMapping, userMapping1, userMapping2)));

    // Invoke
    var members = procurementProjectService.getProjectTeamMembers(PROC_PROJECT_ID, PRINCIPAL);
    assertEquals(3, members.size());
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

  @Test
  void dashboardStatusShouldBeReturnForTheEventsForProjects() {

    // Mock behaviours
    var event = new ProcurementEvent();
    event.setId(EVENT_ID);
    event.setExternalEventId("rfq_0001");
    event.setEventName(EVENT_NAME);
    event.setEventType("RFI");
    event.setOcdsAuthorityName("ocds");
    event.setOcidPrefix("pfhb7i");
    event.setTenderStatus("planned");
    event.setUpdatedAt(Instant.now());
    Set<ProcurementEvent> events = new HashSet<>();
    events.add(event);

    var exportRfxResponse = new ExportRfxResponse();
    exportRfxResponse.setRfxSetting(
        RfxSetting.builder().rfxId("rfq_0001").status("To be Evaluated").statusCode(0).build());

    var project = ProcurementProject.builder().id(PROC_PROJECT_ID).projectName(PROJ_NAME)
        .externalProjectId("Test").procurementEvents(events).build();
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(retryableTendersDBDelegate
        .findProjectUserMappingByUserId(eq(JAGGAER_USER.get().getUserId()),any(),any(), any(Pageable.class)))
            .thenReturn(List.of(ProjectUserMapping.builder()
                .project(ProcurementProject.builder().id(1).procurementEvents(events).build()).id(1)
                .userId("1234").build()));
    when(jaggaerService.searchRFx(Set.of(event.getExternalEventId())))
        .thenReturn(Set.of(exportRfxResponse));
    when(retryableTendersDBDelegate.findByExternalProjectIdIn(any(Set.class)))
        .thenReturn(Arrays.asList(project));

    var response = procurementProjectService.getProjects(PRINCIPAL, null, null, "0", "20");

    assertNotNull(response);
    assertEquals(1, response.size());
    assertEquals(DashboardStatus.IN_PROGRESS,
        response.stream().findFirst().get().getActiveEvent().getDashboardStatus());

  }

}
